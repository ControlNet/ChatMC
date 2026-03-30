package space.controlnet.mineagent.common.gametest;

import dev.langchain4j.model.chat.ChatModel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.core.agent.LlmRuntime;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.session.PersistedSessions;
import space.controlnet.mineagent.core.session.SessionSnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentGameTestSupport {
    private AgentGameTestSupport() {
    }

    public static void initializeRuntime(GameTestHelper helper) {
        MinecraftServer server = requireNonNull("agent-runtime/init/server", helper.getLevel().getServer());
        MineAgentNetwork.setServer(server);
        MineAgentNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
    }

    public static void resetRuntime() {
        resetRuntimeState();
        GameTestRuntimeLease.release();
    }

    public static void resetRuntimeState() {
        MineAgentNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
        MineAgentNetwork.setServer(null);
        clearSessionLocale();
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
            throw new AssertionError("agent-runtime/invoke-handle-chat-packet", rootCause(exception));
        }
    }

    public static ChatModel installChatModel(ChatModel model) {
        return llmModelRef().getAndSet(model);
    }

    public static void restoreChatModel(ChatModel previousModel) {
        llmModelRef().set(previousModel);
    }

    public static RecipeIndexManager recipeIndexManager() {
        try {
            Field field = MineAgent.RECIPE_INDEX.getClass().getDeclaredField("indexManager");
            field.setAccessible(true);
            return (RecipeIndexManager) field.get(MineAgent.RECIPE_INDEX);
        } catch (Exception exception) {
            throw new AssertionError("agent-runtime/read-recipe-index-manager", exception);
        }
    }

    public static void rebuildReadySnapshot(RecipeIndexManager manager, String assertionName) {
        CompletableFuture<Void> future = manager.rebuildAsync(AgentReliabilityGameTestScenarios::emptySnapshot);
        awaitFuture(assertionName, future, Duration.ofSeconds(8));
    }

    public static void awaitFuture(String assertionName, CompletableFuture<?> future, Duration timeout) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> future did not complete", rootCause(exception));
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
            throw new AssertionError("agent-runtime/read-llm-model-ref", rootCause(exception));
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearSessionLocale() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("agent-runtime/clear-session-locale", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearViewerState() {
        try {
            Field viewersField = MineAgentNetwork.class.getDeclaredField("VIEWERS_BY_SESSION");
            viewersField.setAccessible(true);
            ((Map<UUID, java.util.Set<UUID>>) viewersField.get(null)).clear();

            Field byViewerField = MineAgentNetwork.class.getDeclaredField("SESSION_BY_VIEWER");
            byViewerField.setAccessible(true);
            ((Map<UUID, UUID>) byViewerField.get(null)).clear();
        } catch (Exception exception) {
            throw new AssertionError("agent-runtime/clear-viewer-state", exception);
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
}
