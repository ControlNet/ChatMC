package space.controlnet.chatmc.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.gametest.GameTestRuntimeLease;
import space.controlnet.chatmc.common.recipes.RecipeIndexReloadListener;
import space.controlnet.chatmc.core.recipes.RecipeIndexManager;
import space.controlnet.chatmc.core.recipes.RecipeIndexSnapshot;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@PrefixGameTestTemplate(false)
@GameTestHolder("chatmc")
public final class IndexingGateRecoveryGameTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String PLAYER_NAME = "indexing_recovery";

    private IndexingGateRecoveryGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "chatmc")
    public static void indexingGateRecoveryAcrossReload(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> indexingGateRecoveryAcrossReloadInternal(helper));
    }

    private static void indexingGateRecoveryAcrossReloadInternal(GameTestHelper helper) {
        resetSharedNetworkState(false);
        RecipeIndexManager recipeIndexManager = recipeIndexManager();

        try {
            rebuildReadySnapshot(recipeIndexManager, "task7/setup/index-ready-baseline");
            requireTrue("task7/setup/index-ready-confirmed", ChatMC.RECIPE_INDEX.isReady());

            ServerPlayer player = FakePlayerFactory.get(
                    helper.getLevel(),
                    new GameProfile(PLAYER_ID, PLAYER_NAME)
            );

            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task7/setup/active-session -> missing active session"));

            SessionSnapshot initial = requireSnapshot("task7/setup/initial", sessionId);
            requireEquals("task7/setup/initial-state", SessionState.IDLE, initial.state());

            BlockingRebuildBarrier barrier = new BlockingRebuildBarrier();
            CompletableFuture<Void> pendingRebuild = recipeIndexManager.rebuildAsync(() -> {
                barrier.signalStarted();
                barrier.awaitRelease("task7/rebuild/pending-await-release", Duration.ofSeconds(3));
                return emptySnapshot();
            });

            barrier.awaitStarted("task7/rebuild/pending-started", Duration.ofSeconds(3));
            requireTrue("task7/rebuild/index-not-ready-while-pending", !ChatMC.RECIPE_INDEX.isReady());
            requireTrue("task7/rebuild/pending-future-not-done", !pendingRebuild.isDone());

            ChatMCNetwork.onTerminalOpened(player);
            ChatMCNetwork.sendSessionSnapshot(player);

            SessionSnapshot gated = requireSnapshot("task7/gate/state", sessionId);
            requireEquals("task7/gate/enters-indexing", SessionState.INDEXING, gated.state());
            requireTrue(
                    "task7/gate/request-path-blocked-while-indexing",
                    !ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            barrier.release();
            awaitFuture("task7/rebuild/pending-future-completes", pendingRebuild, Duration.ofSeconds(8));
            requireTrue("task7/rebuild/index-ready-after-release", ChatMC.RECIPE_INDEX.isReady());

            ChatMCNetwork.sendSessionSnapshot(player);
            SessionSnapshot recovered = requireSnapshot("task7/recovery/state", sessionId);
            requireEquals("task7/recovery/returns-to-idle", SessionState.IDLE, recovered.state());
            requireTrue("task7/recovery/request-path-reopened", ChatMCNetwork.SESSIONS.tryStartThinking(sessionId));

            ChatMCNetwork.SESSIONS.setState(sessionId, SessionState.DONE);

            MinecraftServer server = helper.getLevel().getServer();
            requireTrue("task7/reload/server-present", server != null);

            RecipeIndexReloadListener reloadListener = new RecipeIndexReloadListener(ChatMC.RECIPE_INDEX, () -> server);
            CompletableFuture<Void> previousFuture = ChatMC.RECIPE_INDEX.indexingFuture().orElse(null);

            CompletableFuture<Void> reloadDispatch = reloadListener.reload(
                    new ImmediatePreparationBarrier(),
                    server.getResourceManager(),
                    InactiveProfiler.INSTANCE,
                    InactiveProfiler.INSTANCE,
                    Runnable::run,
                    Runnable::run
            );
            awaitFuture("task7/reload/reload-dispatch-completes", reloadDispatch, Duration.ofSeconds(2));

            CompletableFuture<Void> reloadRebuildFuture = awaitNewIndexingFuture(
                    "task7/reload/new-indexing-future",
                    previousFuture,
                    Duration.ofSeconds(5)
            );
            awaitFuture("task7/reload/rebuild-future-completes", reloadRebuildFuture, Duration.ofSeconds(30));
            requireTrue("task7/reload/index-ready-after-completion", ChatMC.RECIPE_INDEX.isReady());

            ChatMCNetwork.onTerminalOpened(player);
            ChatMCNetwork.sendSessionSnapshot(player);
            SessionSnapshot secondCycle = requireSnapshot("task7/non-sticky/second-cycle-state", sessionId);
            requireTrue(
                    "task7/non-sticky/second-cycle-does-not-stick-indexing",
                    secondCycle.state() != SessionState.INDEXING
            );
            requireTrue(
                    "task7/non-sticky/second-cycle-request-path-open",
                    ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            helper.succeed();
        } finally {
            rebuildReadySnapshot(recipeIndexManager, "task7/cleanup/index-ready-reset");
            resetSharedNetworkState(true);
        }
    }

    private static SessionSnapshot requireSnapshot(String assertionName, UUID sessionId) {
        return ChatMCNetwork.SESSIONS.get(sessionId)
                .orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
    }

    private static RecipeIndexSnapshot emptySnapshot() {
        return new RecipeIndexSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static void rebuildReadySnapshot(RecipeIndexManager manager, String assertionName) {
        CompletableFuture<Void> future = manager.rebuildAsync(IndexingGateRecoveryGameTest::emptySnapshot);
        awaitFuture(assertionName, future, Duration.ofSeconds(8));
    }

    private static CompletableFuture<Void> awaitNewIndexingFuture(
            String assertionName,
            CompletableFuture<Void> previous,
            Duration timeout
    ) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Optional<CompletableFuture<Void>> candidate = ChatMC.RECIPE_INDEX.indexingFuture();
            if (candidate.isPresent()) {
                CompletableFuture<Void> current = candidate.get();
                if (previous == null || current != previous) {
                    return current;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError(assertionName + " -> timed out waiting for a new indexing future");
    }

    private static void awaitFuture(String assertionName, CompletableFuture<?> future, Duration timeout) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> future did not complete", rootCause(exception));
        }
    }

    private static void resetSharedNetworkState(boolean releaseLease) {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        if (releaseLease) {
            GameTestRuntimeLease.release();
        }
    }

    private static void clearSessionLocale() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task7/cleanup/clear-session-locale", exception);
        }
    }

    private static RecipeIndexManager recipeIndexManager() {
        try {
            Field field = ChatMC.RECIPE_INDEX.getClass().getDeclaredField("indexManager");
            field.setAccessible(true);
            return (RecipeIndexManager) field.get(ChatMC.RECIPE_INDEX);
        } catch (Exception exception) {
            throw new AssertionError("task7/setup/read-recipe-index-manager", exception);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void requireTrue(String assertionName, boolean condition) {
        if (!condition) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static void awaitLatch(String assertionName, CountDownLatch latch, Duration timeout) {
        try {
            if (latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError(assertionName + " -> interrupted", interruptedException);
        }
        throw new AssertionError(assertionName + " -> timed out after " + timeout.toMillis() + " ms");
    }

    private static final class BlockingRebuildBarrier {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void signalStarted() {
            started.countDown();
        }

        private void awaitStarted(String assertionName, Duration timeout) {
            awaitLatch(assertionName, started, timeout);
        }

        private void awaitRelease(String assertionName, Duration timeout) {
            awaitLatch(assertionName, release, timeout);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class ImmediatePreparationBarrier implements PreparableReloadListener.PreparationBarrier {
        @Override
        public <T> CompletableFuture<T> wait(T value) {
            return CompletableFuture.completedFuture(value);
        }
    }
}
