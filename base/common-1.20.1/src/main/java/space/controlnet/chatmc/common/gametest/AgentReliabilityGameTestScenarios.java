package space.controlnet.chatmc.common.gametest;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.recipes.RecipeIndexSnapshot;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolPayload;
import space.controlnet.chatmc.core.tools.ToolRender;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class AgentReliabilityGameTestScenarios {
    private static final int DIRECT_STAGE_ASSERT_DELAY_TICKS = 20;
    private static final int TOOL_STAGE_ASSERT_DELAY_TICKS = 40;
    private static final int ERROR_STAGE_ASSERT_DELAY_TICKS = 20;

    private static final UUID AGENT_RESPONSE_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000181");
    private static final UUID AGENT_TOOL_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000182");
    private static final UUID AGENT_PARSE_ERROR_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000183");
    private static final UUID AGENT_EXCEPTION_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000184");
    private static final String AGENT_RESPONSE_PLAYER_NAME = "agent_response";
    private static final String AGENT_TOOL_PLAYER_NAME = "agent_tool";
    private static final String AGENT_PARSE_ERROR_PLAYER_NAME = "agent_parse_error";
    private static final String AGENT_EXCEPTION_PLAYER_NAME = "agent_model_exception";

    private AgentReliabilityGameTestScenarios() {
    }

    public static void run(GameTestHelper helper, PlayerFactory playerFactory) {
        runDirectResponseStage(helper, playerFactory);
    }

    static RecipeIndexSnapshot emptySnapshot() {
        return new RecipeIndexSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static void runDirectResponseStage(GameTestHelper helper, PlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task18/direct/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = playerFactory.create(helper, AGENT_RESPONSE_PLAYER_ID, AGENT_RESPONSE_PLAYER_NAME);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task18/direct/setup/player", server, player);
        AgentGameTestSupport.rebuildReadySnapshot(
                AgentGameTestSupport.recipeIndexManager(),
                "task18/direct/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Answer directly\",\"tool\":\"response\",\"args\":{\"message\":\"Use eight planks around the perimeter.\"}}"
        );
        ChatModel previousModel = AgentGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/direct/setup/active-session -> missing active session"));

            AgentGameTestSupport.invokeHandleChatPacket(player, "How do I craft a chest?", "en_us", "");

            helper.runAfterDelay(DIRECT_STAGE_ASSERT_DELAY_TICKS, () -> {
                try {
                    SessionSnapshot snapshot = AgentGameTestSupport.requireSnapshot("task18/direct/final-snapshot", sessionId);
                    AgentGameTestSupport.requireEquals("task18/direct/final-state", SessionState.DONE, snapshot.state());
                    AgentGameTestSupport.requireEquals("task18/direct/message-count", 2, snapshot.messages().size());
                    AgentGameTestSupport.requireEquals("task18/direct/user-role", ChatRole.USER, snapshot.messages().get(0).role());
                    AgentGameTestSupport.requireEquals("task18/direct/user-message", "How do I craft a chest?", snapshot.messages().get(0).text());
                    AgentGameTestSupport.requireEquals("task18/direct/assistant-role", ChatRole.ASSISTANT, snapshot.messages().get(1).role());
                    AgentGameTestSupport.requireEquals(
                            "task18/direct/assistant-message",
                            "Use eight planks around the perimeter.",
                            snapshot.messages().get(1).text()
                    );
                    AgentGameTestSupport.requireEquals("task18/direct/model-request-count", 1, model.requestCount());
                    AgentGameTestSupport.requireContains(
                            "task18/direct/prompt-includes-user-message",
                            model.firstPrompt(),
                            "How do I craft a chest?"
                    );

                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntimeState();
                    runToolLoopStage(helper, playerFactory);
                } catch (Throwable throwable) {
                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntime();
                    throw throwable;
                }
            });
        } catch (Throwable throwable) {
            AgentGameTestSupport.restoreChatModel(previousModel);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void runToolLoopStage(GameTestHelper helper, PlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task18/tool/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = playerFactory.create(helper, AGENT_TOOL_PLAYER_ID, AGENT_TOOL_PLAYER_NAME);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task18/tool/setup/player", server, player);
        AgentGameTestSupport.rebuildReadySnapshot(
                AgentGameTestSupport.recipeIndexManager(),
                "task18/tool/setup/index-ready"
        );

        String providerId = uniqueName("task18-tool-provider");
        String toolName = uniqueName("task18-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName);
        ToolRegistry.register(providerId, provider);

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Need to inspect inventory\",\"tool\":\"" + toolName + "\",\"args\":{\"slot\":2}}",
                "{\"tool\":\"response\",\"args\":{\"message\":\"Inventory lookup finished.\"}}"
        );
        ChatModel previousModel = AgentGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/tool/setup/active-session -> missing active session"));

            AgentGameTestSupport.invokeHandleChatPacket(player, "Check my inventory", "en_us", "");

            helper.runAfterDelay(TOOL_STAGE_ASSERT_DELAY_TICKS, () -> {
                try {
                    SessionSnapshot snapshot = AgentGameTestSupport.requireSnapshot("task18/tool/final-snapshot", sessionId);
                    AgentGameTestSupport.requireEquals("task18/tool/final-state", SessionState.DONE, snapshot.state());
                    AgentGameTestSupport.requireEquals("task18/tool/model-request-count", 2, model.requestCount());
                    AgentGameTestSupport.requireEquals("task18/tool/provider-execute-count", 1, provider.executeCount());
                    AgentGameTestSupport.requireEquals("task18/tool/message-count", 3, snapshot.messages().size());
                    AgentGameTestSupport.requireEquals("task18/tool/user-role", ChatRole.USER, snapshot.messages().get(0).role());
                    AgentGameTestSupport.requireEquals("task18/tool/tool-role", ChatRole.TOOL, snapshot.messages().get(1).role());
                    AgentGameTestSupport.requireContains(
                            "task18/tool/tool-payload-has-tool-name",
                            snapshot.messages().get(1).text(),
                            toolName
                    );
                    AgentGameTestSupport.requireEquals(
                            "task18/tool/assistant-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(2).role()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task18/tool/assistant-message",
                            "Inventory lookup finished.",
                            snapshot.messages().get(2).text()
                    );

                    ToolRegistry.unregister(providerId);
                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntimeState();
                    runInvalidModelStage(helper, playerFactory);
                } catch (Throwable throwable) {
                    ToolRegistry.unregister(providerId);
                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntime();
                    throw throwable;
                }
            });
        } catch (Throwable throwable) {
            ToolRegistry.unregister(providerId);
            AgentGameTestSupport.restoreChatModel(previousModel);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void runInvalidModelStage(GameTestHelper helper, PlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task18/error/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = playerFactory.create(helper, AGENT_PARSE_ERROR_PLAYER_ID, AGENT_PARSE_ERROR_PLAYER_NAME);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task18/error/setup/player", server, player);
        AgentGameTestSupport.rebuildReadySnapshot(
                AgentGameTestSupport.recipeIndexManager(),
                "task18/error/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel("not-json-at-all");
        ChatModel previousModel = AgentGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/error/setup/active-session -> missing active session"));

            AgentGameTestSupport.invokeHandleChatPacket(player, "Do something unreliable", "en_us", "");

            helper.runAfterDelay(ERROR_STAGE_ASSERT_DELAY_TICKS, () -> {
                try {
                    SessionSnapshot snapshot = AgentGameTestSupport.requireSnapshot("task18/error/final-snapshot", sessionId);
                    AgentGameTestSupport.requireEquals("task18/error/final-state", SessionState.FAILED, snapshot.state());
                    AgentGameTestSupport.requireEquals("task18/error/model-request-count", 1, model.requestCount());
                    AgentGameTestSupport.requireEquals(
                            "task18/error/last-error",
                            Optional.of("LLM failed to produce a decision"),
                            snapshot.lastError()
                    );
                    AgentGameTestSupport.requireEquals("task18/error/message-count", 2, snapshot.messages().size());
                    AgentGameTestSupport.requireEquals("task18/error/error-role", ChatRole.ASSISTANT, snapshot.messages().get(1).role());
                    AgentGameTestSupport.requireEquals(
                            "task18/error/error-message",
                            "Error: LLM failed to produce a decision",
                            snapshot.messages().get(1).text()
                    );
                } finally {
                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntimeState();
                }

                runModelExceptionStage(helper, playerFactory);
            });
        } catch (Throwable throwable) {
            AgentGameTestSupport.restoreChatModel(previousModel);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void runModelExceptionStage(GameTestHelper helper, PlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task18/exception/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = playerFactory.create(helper, AGENT_EXCEPTION_PLAYER_ID, AGENT_EXCEPTION_PLAYER_NAME);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task18/exception/setup/player", server, player);
        AgentGameTestSupport.rebuildReadySnapshot(
                AgentGameTestSupport.recipeIndexManager(),
                "task18/exception/setup/index-ready"
        );

        ThrowingChatModel model = new ThrowingChatModel();
        ChatModel previousModel = AgentGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/exception/setup/active-session -> missing active session"));

            AgentGameTestSupport.invokeHandleChatPacket(player, "Trigger a model exception", "en_us", "");

            helper.runAfterDelay(ERROR_STAGE_ASSERT_DELAY_TICKS, () -> {
                try {
                    SessionSnapshot snapshot = AgentGameTestSupport.requireSnapshot("task18/exception/final-snapshot", sessionId);
                    AgentGameTestSupport.requireEquals("task18/exception/final-state", SessionState.FAILED, snapshot.state());
                    AgentGameTestSupport.requireEquals("task18/exception/model-request-count", 1, model.requestCount());
                    AgentGameTestSupport.requireEquals(
                            "task18/exception/last-error",
                            Optional.of("LLM failed to produce a decision"),
                            snapshot.lastError()
                    );
                    AgentGameTestSupport.requireEquals("task18/exception/message-count", 2, snapshot.messages().size());
                    AgentGameTestSupport.requireEquals("task18/exception/error-role", ChatRole.ASSISTANT, snapshot.messages().get(1).role());
                    AgentGameTestSupport.requireEquals(
                            "task18/exception/error-message",
                            "Error: LLM failed to produce a decision",
                            snapshot.messages().get(1).text()
                    );
                    helper.succeed();
                } finally {
                    AgentGameTestSupport.restoreChatModel(previousModel);
                    AgentGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            AgentGameTestSupport.restoreChatModel(previousModel);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    public interface PlayerFactory {
        ServerPlayer create(GameTestHelper helper, UUID playerId, String playerName);
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> scriptedResponses = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final List<String> prompts = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicInteger requestCount = new AtomicInteger();

        private ScriptedChatModel(String... scriptedResponses) {
            this.scriptedResponses.addAll(List.of(scriptedResponses));
        }

        private int requestCount() {
            return requestCount.get();
        }

        private String firstPrompt() {
            return prompts.isEmpty() ? "" : prompts.get(0);
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requestCount.incrementAndGet();
            prompts.add(chatRequest.messages().toString());

            String response = scriptedResponses.poll();
            if (response == null) {
                throw new IllegalStateException("task18/scripted-model/no-scripted-response");
            }

            return ChatResponse.builder()
                    .aiMessage(new AiMessage(response))
                    .build();
        }
    }

    private static final class ThrowingChatModel implements ChatModel {
        private final AtomicInteger requestCount = new AtomicInteger();

        private int requestCount() {
            return requestCount.get();
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requestCount.incrementAndGet();
            throw new IllegalStateException("task18/model-exception/boom");
        }
    }

    private static final class ControlledToolProvider implements ToolProvider {
        private final AgentTool toolSpec;
        private final AtomicInteger executeCount = new AtomicInteger();

        private ControlledToolProvider(String toolName) {
            this.toolSpec = new StaticAgentTool(toolName);
        }

        private int executeCount() {
            return executeCount.get();
        }

        @Override
        public List<AgentTool> specs() {
            return List.of(toolSpec);
        }

        @Override
        public space.controlnet.chatmc.core.tools.ToolOutcome execute(
                Optional<space.controlnet.chatmc.core.terminal.TerminalContext> terminal,
                ToolCall call,
                boolean approved
        ) {
            executeCount.incrementAndGet();
            return space.controlnet.chatmc.core.tools.ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
        }
    }

    private static final class StaticAgentTool implements AgentTool {
        private final String name;

        private StaticAgentTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Shared agent reliability test tool";
        }

        @Override
        public String argsSchema() {
            return "{}";
        }

        @Override
        public List<String> argsDescription() {
            return List.of();
        }

        @Override
        public String resultSchema() {
            return "{}";
        }

        @Override
        public List<String> resultDescription() {
            return List.of();
        }

        @Override
        public List<String> examples() {
            return List.of();
        }

        @Override
        public ToolRender render(ToolPayload payload) {
            return null;
        }
    }
}
