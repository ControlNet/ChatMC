package space.controlnet.mineagent.fabric.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.langchain4j.model.chat.ChatModel;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;
import space.controlnet.mineagent.common.recipes.RecipeIndexReloadListener;
import space.controlnet.mineagent.common.terminal.TerminalContextRegistry;
import space.controlnet.mineagent.common.terminal.TerminalContextResolver;
import space.controlnet.mineagent.core.agent.LlmRuntime;
import space.controlnet.mineagent.core.agent.AgentLoopResult;
import space.controlnet.mineagent.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.session.PersistedSessions;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class MineAgentFabricGameTestSupport {
    private static final Object UNSUPPORTED_ARGUMENT = new Object();

    private MineAgentFabricGameTestSupport() {
    }

    public static void initializeRuntime(GameTestHelper helper) {
        MinecraftServer server = requireNonNull("runtime/init/server", helper.getLevel().getServer());
        MineAgentNetwork.setServer(server);
        MineAgentNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
    }

    public static void resetRuntime() {
        MineAgentNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
        MineAgentNetwork.setServer(null);
        clearSessionLocale();
        GameTestRuntimeLease.release();
    }

    public static ServerPlayer createServerPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        MinecraftServer server = requireNonNull("runtime/create-player/server", helper.getLevel().getServer());
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(playerId, playerName);

        for (Constructor<?> constructor : ServerPlayer.class.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < 3) {
                continue;
            }
            if (!MinecraftServer.class.isAssignableFrom(parameterTypes[0])
                    || !ServerLevel.class.isAssignableFrom(parameterTypes[1])
                    || !GameProfile.class.isAssignableFrom(parameterTypes[2])) {
                continue;
            }

            Object[] args = new Object[parameterTypes.length];
            args[0] = server;
            args[1] = level;
            args[2] = profile;

            boolean supported = true;
            for (int index = 3; index < parameterTypes.length; index++) {
                Object argument = defaultConstructorArgument(parameterTypes[index]);
                if (argument == UNSUPPORTED_ARGUMENT) {
                    supported = false;
                    break;
                }
                args[index] = argument;
            }

            if (!supported) {
                continue;
            }

            try {
                constructor.setAccessible(true);
                ServerPlayer player = (ServerPlayer) constructor.newInstance(args);
                player.setPos(0.5D, 2.0D, 0.5D);
                return player;
            } catch (Exception ignored) {
            }
        }

        throw new AssertionError("runtime/create-player -> unable to instantiate ServerPlayer for Fabric runtime tests");
    }

    public static void ensurePlayerResolvableByChatNetwork(
            String assertionPrefix,
            MinecraftServer server,
            ServerPlayer player
    ) {
        UUID playerId = requireNonNull(assertionPrefix + "/id", player.getUUID());
        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
            return;
        }

        Object playerList = requireNonNull(assertionPrefix + "/player-list", server.getPlayerList());
        requireTrue(
                assertionPrefix + "/inject-player-lookup",
                injectPlayerLookup(playerList, playerId, player)
        );
        requireTrue(assertionPrefix + "/lookup-present", MineAgentNetwork.findPlayer(playerId).isPresent());
    }

    public static SessionSnapshot requireSnapshot(String assertionName, UUID sessionId) {
        return MineAgentNetwork.SESSIONS.get(sessionId)
                .orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
    }

    public static int protocolVersion() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("PROTOCOL_VERSION");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-protocol-version", exception);
        }
    }

    public static void invokeHandleAgentLoopResult(
            ServerPlayer player,
            AgentLoopResult result,
            UUID sessionId,
            TerminalBinding binding,
            String locale
    ) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "handleAgentLoopResult",
                    ServerPlayer.class,
                    AgentLoopResult.class,
                    UUID.class,
                    TerminalBinding.class,
                    String.class
            );
            method.setAccessible(true);
            method.invoke(null, player, result, sessionId, binding, locale);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-handle-agent-loop-result", rootCause(exception));
        }
    }

    public static void invokeHandleApprovalDecision(ServerPlayer player, C2SApprovalDecisionPacket packet) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "handleApprovalDecision",
                    ServerPlayer.class,
                    C2SApprovalDecisionPacket.class
            );
            method.setAccessible(true);
            method.invoke(null, player, packet);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-handle-approval", rootCause(exception));
        }
    }

    public static void invokeHandleOpenSession(ServerPlayer player, UUID sessionId) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "handleOpenSession",
                    ServerPlayer.class,
                    UUID.class
            );
            method.setAccessible(true);
            method.invoke(null, player, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-handle-open-session", rootCause(exception));
        }
    }

    public static void invokeBroadcastSessionSnapshot(UUID sessionId) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod("broadcastSessionSnapshot", UUID.class);
            method.setAccessible(true);
            method.invoke(null, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-broadcast-session-snapshot", rootCause(exception));
        }
    }

    public static void invokeHandleDeleteSession(ServerPlayer player, UUID sessionId) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "handleDeleteSession",
                    ServerPlayer.class,
                    UUID.class
            );
            method.setAccessible(true);
            method.invoke(null, player, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-handle-delete-session", rootCause(exception));
        }
    }

    public static void invokeHandleChatPacket(
            ServerPlayer player,
            String text,
            String clientLocale,
            String aiLocaleOverride
    ) {
        try {
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "handleChatPacket",
                    ServerPlayer.class,
                    String.class,
                    String.class,
                    String.class
            );
            method.setAccessible(true);
            method.invoke(null, player, text, clientLocale, aiLocaleOverride);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-handle-chat-packet", rootCause(exception));
        }
    }

    public static ChatModel installChatModel(ChatModel model) {
        try {
            return llmModelRef().getAndSet(model);
        } catch (Exception exception) {
            throw new AssertionError("runtime/install-chat-model", rootCause(exception));
        }
    }

    public static void restoreChatModel(ChatModel previousModel) {
        try {
            llmModelRef().set(previousModel);
        } catch (Exception exception) {
            throw new AssertionError("runtime/restore-chat-model", rootCause(exception));
        }
    }

    public static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.mineagent.common.agent.McSessionContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return constructor.newInstance(playerId);
        } catch (Exception exception) {
            throw new AssertionError("runtime/new-mc-session-context", exception);
        }
    }

    public static ToolOutcome invokeMcSessionExecuteTool(Object sessionContext, ToolCall call, boolean approved) {
        try {
            Method executeMethod = sessionContext.getClass().getDeclaredMethod(
                    "executeTool",
                    Optional.class,
                    ToolCall.class,
                    boolean.class
            );
            executeMethod.setAccessible(true);
            return (ToolOutcome) executeMethod.invoke(sessionContext, Optional.empty(), call, approved);
        } catch (Exception exception) {
            throw new AssertionError("runtime/invoke-mc-session-execute-tool", rootCause(exception));
        }
    }

    public static RecipeIndexManager recipeIndexManager() {
        try {
            Field field = MineAgent.RECIPE_INDEX.getClass().getDeclaredField("indexManager");
            field.setAccessible(true);
            return (RecipeIndexManager) field.get(MineAgent.RECIPE_INDEX);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-recipe-index-manager", exception);
        }
    }

    public static void rebuildReadySnapshot(RecipeIndexManager manager, String assertionName) {
        CompletableFuture<Void> future = manager.rebuildAsync(MineAgentFabricRuntimeGameTests::emptySnapshot);
        awaitFuture(assertionName, future, Duration.ofSeconds(8));
    }

    public static CompletableFuture<Void> awaitNewIndexingFuture(
            String assertionName,
            CompletableFuture<Void> previous,
            Duration timeout
    ) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Optional<CompletableFuture<Void>> candidate = MineAgent.RECIPE_INDEX.indexingFuture();
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

    public static void awaitFuture(String assertionName, CompletableFuture<?> future, Duration timeout) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> future did not complete", rootCause(exception));
        }
    }

    public static void awaitLatch(String assertionName, CountDownLatch latch, Duration timeout) {
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

    @SuppressWarnings("unchecked")
    public static AtomicReference<TerminalContextResolver> resolverRef() {
        try {
            Field field = TerminalContextRegistry.class.getDeclaredField("RESOLVER");
            field.setAccessible(true);
            return (AtomicReference<TerminalContextResolver>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-resolver-ref", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<UUID, Set<UUID>> viewersBySession() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("VIEWERS_BY_SESSION");
            field.setAccessible(true);
            return (Map<UUID, Set<UUID>>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-viewers-by-session", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<UUID, UUID> sessionByViewer() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("SESSION_BY_VIEWER");
            field.setAccessible(true);
            return (Map<UUID, UUID>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-session-by-viewer", exception);
        }
    }

    public static Set<UUID> viewersForSession(UUID sessionId) {
        Set<UUID> viewers = viewersBySession().get(sessionId);
        if (viewers == null) {
            return Set.of();
        }
        return Set.copyOf(viewers);
    }

    public static void clearViewerState() {
        viewersBySession().clear();
        sessionByViewer().clear();
    }

    public static void clearSessionLocale() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("runtime/clear-session-locale", exception);
        }
    }

    public static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    public static void requireNull(String assertionName, Object value) {
        if (value == null) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected null, actual: " + value);
    }

    public static void requireTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    public static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    public static void requireContains(String assertionName, String value, String expectedSubstring) {
        if (value != null && value.contains(expectedSubstring)) {
            return;
        }
        throw new AssertionError(
                assertionName + " -> expected substring '" + expectedSubstring + "' in value: " + value
        );
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<ChatModel> llmModelRef() {
        try {
            Field field = LlmRuntime.class.getDeclaredField("MODEL");
            field.setAccessible(true);
            return (AtomicReference<ChatModel>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("runtime/read-llm-model-ref", rootCause(exception));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean injectPlayerLookup(Object playerList, UUID playerId, ServerPlayer player) {
        Class<?> currentClass = playerList.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                field.setAccessible(true);

                try {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        Map map = (Map) field.get(playerList);
                        if (map == null) {
                            continue;
                        }

                        Object previous = map.put(playerId, player);
                        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (previous == null) {
                            map.remove(playerId);
                        } else {
                            map.put(playerId, previous);
                        }
                    } else if (List.class.isAssignableFrom(field.getType())) {
                        List list = (List) field.get(playerList);
                        if (list == null) {
                            continue;
                        }

                        boolean added = false;
                        if (!list.contains(player)) {
                            list.add(player);
                            added = true;
                        }

                        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (added) {
                            list.remove(player);
                        }
                    }
                } catch (IllegalAccessException | UnsupportedOperationException | ClassCastException ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    private static Object defaultConstructorArgument(Class<?> parameterType) {
        if (!parameterType.isPrimitive()) {
            Object defaultFactoryValue = invokeDefaultFactory(parameterType);
            if (defaultFactoryValue != UNSUPPORTED_ARGUMENT) {
                return defaultFactoryValue;
            }

            try {
                Constructor<?> constructor = parameterType.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Exception ignored) {
            }

            return null;
        }

        if (parameterType == boolean.class) {
            return false;
        }
        if (parameterType == byte.class) {
            return (byte) 0;
        }
        if (parameterType == short.class) {
            return (short) 0;
        }
        if (parameterType == int.class) {
            return 0;
        }
        if (parameterType == long.class) {
            return 0L;
        }
        if (parameterType == float.class) {
            return 0.0F;
        }
        if (parameterType == double.class) {
            return 0.0D;
        }
        if (parameterType == char.class) {
            return '\0';
        }
        return UNSUPPORTED_ARGUMENT;
    }

    private static Object invokeDefaultFactory(Class<?> parameterType) {
        for (Method method : parameterType.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            if (!parameterType.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            String methodName = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!methodName.contains("default")) {
                continue;
            }

            try {
                method.setAccessible(true);
                return method.invoke(null);
            } catch (Exception ignored) {
            }
        }
        return UNSUPPORTED_ARGUMENT;
    }
}
