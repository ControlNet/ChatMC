package space.controlnet.chatae.common.agent;

import net.minecraft.server.level.ServerPlayer;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.common.ChatAENetwork;
import space.controlnet.chatae.common.llm.PromptRuntime;
import space.controlnet.chatae.common.terminal.TerminalContextFactory;
import space.controlnet.chatae.common.tools.ToolRouter;
import space.controlnet.chatae.core.agent.AgentDecision;
import space.controlnet.chatae.core.agent.AgentLoopResult;
import space.controlnet.chatae.core.agent.AgentReasoningService;
import space.controlnet.chatae.core.agent.ConversationHistoryBuilder;
import space.controlnet.chatae.core.agent.PromptId;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.TerminalBinding;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AgentRunner {
    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_HISTORY_MESSAGES = 20;

    // State keys
    private static final String KEY_PLAYER = "player";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_BINDING = "binding";
    private static final String KEY_LOCALE = "locale";
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_DECISION = "decision";
    private static final String KEY_RESULT = "result";

    private static final String TOOL_LIST = String.join(", ",
            "recipes.search",
            "recipes.get",
            "ae2.list_items",
            "ae2.list_craftables",
            "ae2.simulate_craft",
            "ae2.request_craft",
            "ae2.job_status",
            "ae2.job_cancel"
    );

    private static final String ARGS_SCHEMA = "- recipes.search: {query, pageToken?, limit, modId?, recipeType?, outputItemId?, ingredientItemId?, tagId?}\n"
            + "- recipes.get: {recipeId}\n"
            + "- ae2.list_items: {query, craftableOnly, limit, pageToken?}\n"
            + "- ae2.list_craftables: {query, craftableOnly, limit, pageToken?}\n"
            + "- ae2.simulate_craft: {itemId, count}\n"
            + "- ae2.request_craft: {itemId, count, cpuName?}\n"
            + "- ae2.job_status: {jobId}\n"
            + "- ae2.job_cancel: {jobId}.";

    private final CompiledGraph<LoopState> graph;
    private final AgentReasoningService reasoningService;

    public AgentRunner() {
        this.reasoningService = new AgentReasoningService((msg, ex) -> ChatAE.LOGGER.warn(msg, ex));

        try {
            StateGraph<LoopState> graphDef = new StateGraph<>(LoopState::new);

            // Nodes - wrap with AsyncNodeAction.node_async
            graphDef.addNode("reason", AsyncNodeAction.node_async((NodeAction<LoopState>) this::reasonNode));
            graphDef.addNode("execute", AsyncNodeAction.node_async((NodeAction<LoopState>) this::executeNode));
            graphDef.addNode("respond", AsyncNodeAction.node_async((NodeAction<LoopState>) this::respondNode));

            // Edges
            graphDef.addEdge(GraphDefinition.START, "reason");
            graphDef.addConditionalEdges("reason",
                    (AsyncEdgeAction<LoopState>) (state) -> CompletableFuture.completedFuture(routeAfterReason(state)),
                    Map.of(
                            "execute", "execute",
                            "respond", "respond",
                            "end", GraphDefinition.END
                    ));
            graphDef.addConditionalEdges("execute",
                    (AsyncEdgeAction<LoopState>) (state) -> CompletableFuture.completedFuture(routeAfterExecute(state)),
                    Map.of(
                            "reason", "reason",
                            "end", GraphDefinition.END
                    ));
            graphDef.addEdge("respond", GraphDefinition.END);

            this.graph = graphDef.compile(CompileConfig.builder()
                    .recursionLimit(MAX_ITERATIONS + 5)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build agent graph", e);
        }
    }

    /**
     * Run the agent loop for a user message.
     *
     * @param player          The player who sent the message
     * @param sessionId       The session ID
     * @param binding         The terminal binding for proposals
     * @param effectiveLocale The locale for LLM responses
     * @return The result of the agent loop
     */
    public AgentLoopResult runLoop(ServerPlayer player, UUID sessionId, TerminalBinding binding, String effectiveLocale) {
        Map<String, Object> init = new HashMap<>();
        init.put(KEY_PLAYER, player);
        init.put(KEY_SESSION_ID, sessionId);
        init.put(KEY_BINDING, binding);
        init.put(KEY_LOCALE, effectiveLocale);
        init.put(KEY_ITERATION, 0);
        init.put(KEY_DECISION, null);
        init.put(KEY_RESULT, null);

        try {
            Optional<LoopState> finalState = graph.invoke(init);
            if (finalState.isEmpty()) {
                return AgentLoopResult.withError("Agent state is empty", 0);
            }

            LoopState state = finalState.get();
            AgentLoopResult result = state.value(KEY_RESULT)
                    .map(AgentLoopResult.class::cast)
                    .orElse(null);

            if (result != null) {
                return result;
            }

            int iterations = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
            return AgentLoopResult.withError("No result from agent loop", iterations);
        } catch (Exception e) {
            ChatAE.LOGGER.error("Agent loop failed", e);
            return AgentLoopResult.withError("Agent loop failed: " + e.getMessage(), 0);
        }
    }

    /**
     * Reason node: Ask the LLM to decide the next action.
     */
    private Map<String, Object> reasonNode(LoopState state) {
        ServerPlayer player = state.value(KEY_PLAYER).map(ServerPlayer.class::cast).orElse(null);
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        String locale = state.value(KEY_LOCALE).map(String.class::cast).orElse("en_us");
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);

        if (player == null || sessionId == null) {
            return Map.of(KEY_RESULT, AgentLoopResult.withError("Missing player or session", iteration));
        }

        // Check iteration limit
        if (iteration >= MAX_ITERATIONS) {
            return Map.of(KEY_RESULT, AgentLoopResult.withError("Max iterations reached", iteration));
        }

        // Build conversation history from session
        List<ChatMessage> messages = ChatAENetwork.SESSIONS.get(sessionId)
                .map(SessionSnapshot::messages)
                .orElse(List.of());
        String history = ConversationHistoryBuilder.build(messages, MAX_HISTORY_MESSAGES);

        // Render prompt
        Map<String, String> variables = new HashMap<>();
        variables.put("tool_list", TOOL_LIST);
        variables.put("args_schema", ARGS_SCHEMA);
        variables.put("effectiveLocale", locale);
        variables.put("conversation_history", history);

        String prompt = PromptRuntime.render(PromptId.AGENT_REASON, locale, variables);
        ChatAE.LOGGER.debug("Agent reason iteration={} locale={}", iteration, locale);

        // Call LLM
        Optional<AgentDecision> decision = reasoningService.reason(prompt);
        if (decision.isEmpty()) {
            return Map.of(KEY_RESULT, AgentLoopResult.withError("LLM failed to produce a decision", iteration));
        }

        AgentDecision d = decision.get();
        d.thinking().ifPresent(t -> ChatAE.LOGGER.debug("Agent thinking: {}", t));

        return Map.of(
                KEY_DECISION, d,
                KEY_ITERATION, iteration + 1
        );
    }

    /**
     * Route after reason: decide whether to execute a tool, respond, or end.
     */
    private String routeAfterReason(LoopState state) {
        // Check if we have a result (error case)
        if (state.value(KEY_RESULT).isPresent()) {
            return "end";
        }

        AgentDecision decision = state.value(KEY_DECISION)
                .map(AgentDecision.class::cast)
                .orElse(null);

        if (decision == null) {
            return "end";
        }

        if (decision.isToolCall()) {
            return "execute";
        } else if (decision.isRespond()) {
            return "respond";
        }

        return "end";
    }

    /**
     * Execute node: Execute the tool call.
     */
    private Map<String, Object> executeNode(LoopState state) {
        ServerPlayer player = state.value(KEY_PLAYER).map(ServerPlayer.class::cast).orElse(null);
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        TerminalBinding binding = state.value(KEY_BINDING).map(TerminalBinding.class::cast).orElse(null);
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);

        AgentDecision decision = state.value(KEY_DECISION)
                .map(AgentDecision.class::cast)
                .orElse(null);

        if (player == null || decision == null || decision.toolCall().isEmpty()) {
            return Map.of(KEY_RESULT, AgentLoopResult.withError("Invalid state for execution", iteration));
        }

        ToolCall call = decision.toolCall().get();
        ChatAE.LOGGER.debug("Agent executing tool: {} args: {}", call.toolName(), call.argsJson());

        // Get terminal context from player
        Optional<TerminalContext> terminal = TerminalContextFactory.fromPlayer(player);

        // Execute tool
        ToolOutcome outcome = ToolRouter.execute(terminal, call, false);

        // If proposal is needed, return it and exit the loop
        if (outcome.hasProposal()) {
            ChatAE.LOGGER.debug("Agent produced proposal: {}", outcome.proposal().id());
            return Map.of(KEY_RESULT, AgentLoopResult.withProposal(outcome.proposal(), iteration));
        }

        // Append tool result to session
        if (outcome.result() != null) {
            String payload = outcome.result().payloadJson();
            if (payload == null && outcome.result().error() != null) {
                payload = "Error: " + outcome.result().error().message();
            }
            if (payload != null && !payload.isBlank()) {
                ChatAENetwork.SESSIONS.appendMessage(sessionId,
                        new ChatMessage(ChatRole.TOOL, payload, System.currentTimeMillis()));
            }
        }

        // Continue the loop
        return Map.of(KEY_ITERATION, iteration);
    }

    /**
     * Route after execute: decide whether to continue reasoning or end.
     */
    private String routeAfterExecute(LoopState state) {
        // Check if we have a result (proposal case)
        if (state.value(KEY_RESULT).isPresent()) {
            return "end";
        }

        // Continue to reason
        return "reason";
    }

    /**
     * Respond node: Append the response to the session and finish.
     */
    private Map<String, Object> respondNode(LoopState state) {
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);

        AgentDecision decision = state.value(KEY_DECISION)
                .map(AgentDecision.class::cast)
                .orElse(null);

        if (decision == null || decision.response().isEmpty()) {
            return Map.of(KEY_RESULT, AgentLoopResult.withError("No response in decision", iteration));
        }

        String response = decision.response().get();

        // Append response to session
        if (sessionId != null) {
            ChatAENetwork.SESSIONS.appendMessage(sessionId,
                    new ChatMessage(ChatRole.ASSISTANT, response, System.currentTimeMillis()));
        }

        return Map.of(KEY_RESULT, AgentLoopResult.withResponse(response, iteration));
    }

    /**
     * State class for the agent loop.
     */
    private static final class LoopState extends AgentState {
        public LoopState(Map<String, Object> data) {
            super(data);
        }
    }
}
