package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.recipes.RecipeIndexReloadListener;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.SessionState;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public final class IndexingGateRecoveryGameTestScenarios {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String PLAYER_NAME = "indexing_recovery";

    private IndexingGateRecoveryGameTestScenarios() {
    }

    public static void indexingGateRecoveryAcrossReload(GameTestHelper helper, GameTestPlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);
        RecipeIndexManager recipeIndexManager = AgentGameTestSupport.recipeIndexManager();

        try {
            AgentGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task7/setup/index-ready-baseline");
            AgentGameTestSupport.requireTrue("task7/setup/index-ready-confirmed", MineAgent.RECIPE_INDEX.isReady());

            ServerPlayer player = playerFactory.create(helper, PLAYER_ID, PLAYER_NAME);
            player.setPos(0.5D, 2.0D, 0.5D);
            MineAgentNetwork.onTerminalOpened(player);

            UUID sessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task7/setup/active-session -> missing active session"));

            SessionSnapshot initial = AgentGameTestSupport.requireSnapshot("task7/setup/initial", sessionId);
            AgentGameTestSupport.requireEquals("task7/setup/initial-state", SessionState.IDLE, initial.state());

            BlockingRebuildBarrier barrier = new BlockingRebuildBarrier();
            CompletableFuture<Void> pendingRebuild = recipeIndexManager.rebuildAsync(() -> {
                barrier.signalStarted();
                barrier.awaitRelease("task7/rebuild/pending-await-release", Duration.ofSeconds(3));
                return AgentReliabilityGameTestScenarios.emptySnapshot();
            });

            barrier.awaitStarted("task7/rebuild/pending-started", Duration.ofSeconds(3));
            AgentGameTestSupport.requireTrue("task7/rebuild/index-not-ready-while-pending", !MineAgent.RECIPE_INDEX.isReady());
            AgentGameTestSupport.requireTrue("task7/rebuild/pending-future-not-done", !pendingRebuild.isDone());

            MineAgentNetwork.onTerminalOpened(player);
            MineAgentNetwork.sendSessionSnapshot(player);

            SessionSnapshot gated = AgentGameTestSupport.requireSnapshot("task7/gate/state", sessionId);
            AgentGameTestSupport.requireEquals("task7/gate/enters-indexing", SessionState.INDEXING, gated.state());
            AgentGameTestSupport.requireTrue(
                    "task7/gate/request-path-blocked-while-indexing",
                    !MineAgentNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            barrier.release();
            AgentGameTestSupport.awaitFuture(
                    "task7/rebuild/pending-future-completes",
                    pendingRebuild,
                    Duration.ofSeconds(8)
            );
            AgentGameTestSupport.requireTrue("task7/rebuild/index-ready-after-release", MineAgent.RECIPE_INDEX.isReady());

            MineAgentNetwork.sendSessionSnapshot(player);
            SessionSnapshot recovered = AgentGameTestSupport.requireSnapshot("task7/recovery/state", sessionId);
            AgentGameTestSupport.requireEquals("task7/recovery/returns-to-idle", SessionState.IDLE, recovered.state());
            AgentGameTestSupport.requireTrue(
                    "task7/recovery/request-path-reopened",
                    MineAgentNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            MineAgentNetwork.SESSIONS.setState(sessionId, SessionState.DONE);

            MinecraftServer server = AgentGameTestSupport.requireNonNull(
                    "task7/reload/server-present",
                    helper.getLevel().getServer()
            );
            RecipeIndexReloadListener reloadListener = new RecipeIndexReloadListener(MineAgent.RECIPE_INDEX, () -> server);
            CompletableFuture<Void> previousFuture = MineAgent.RECIPE_INDEX.indexingFuture().orElse(null);

            CompletableFuture<Void> reloadDispatch = reloadListener.reload(
                    new ImmediatePreparationBarrier(),
                    server.getResourceManager(),
                    InactiveProfiler.INSTANCE,
                    InactiveProfiler.INSTANCE,
                    Runnable::run,
                    Runnable::run
            );
            AgentGameTestSupport.awaitFuture(
                    "task7/reload/reload-dispatch-completes",
                    reloadDispatch,
                    Duration.ofSeconds(2)
            );

            CompletableFuture<Void> reloadRebuildFuture = AgentGameTestSupport.awaitNewIndexingFuture(
                    "task7/reload/new-indexing-future",
                    previousFuture,
                    Duration.ofSeconds(5)
            );
            AgentGameTestSupport.awaitFuture(
                    "task7/reload/rebuild-future-completes",
                    reloadRebuildFuture,
                    Duration.ofSeconds(30)
            );
            AgentGameTestSupport.requireTrue("task7/reload/index-ready-after-completion", MineAgent.RECIPE_INDEX.isReady());

            MineAgentNetwork.onTerminalOpened(player);
            MineAgentNetwork.sendSessionSnapshot(player);
            SessionSnapshot secondCycle = AgentGameTestSupport.requireSnapshot("task7/non-sticky/second-cycle-state", sessionId);
            AgentGameTestSupport.requireTrue(
                    "task7/non-sticky/second-cycle-does-not-stick-indexing",
                    secondCycle.state() != SessionState.INDEXING
            );
            AgentGameTestSupport.requireTrue(
                    "task7/non-sticky/second-cycle-request-path-open",
                    MineAgentNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task7/cleanup/index-ready-reset");
            AgentGameTestSupport.resetRuntime();
        }
    }

    private static final class BlockingRebuildBarrier {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void signalStarted() {
            started.countDown();
        }

        private void awaitStarted(String assertionName, Duration timeout) {
            AgentGameTestSupport.awaitLatch(assertionName, started, timeout);
        }

        private void awaitRelease(String assertionName, Duration timeout) {
            AgentGameTestSupport.awaitLatch(assertionName, release, timeout);
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
