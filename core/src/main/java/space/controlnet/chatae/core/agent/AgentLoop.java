package space.controlnet.chatae.core.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import space.controlnet.chatae.core.audit.AuditLogger;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.TerminalBinding;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;
import space.controlnet.chatae.core.tools.ToolMessagePayload;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core agent loop implementation using LangGraph4j.
 * This class contains all the AI agent logic without MC dependencies.
 */
public final class AgentLoop {
    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    private static final long DEFAULT_COOLDOWN_MS = 1500;

    // State keys
    private static final String KEY_PLAYER = "player";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_LOCALE = "locale";
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_DECISION = "decision";
    private static final String KEY_RESULT = "result";
    private static final String KEY_CONTEXT = "context";

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
    private final LlmRateLimiter rateLimiter;
    private final ExecutorService llmExecutor;
    private final AtomicLong timeoutMs;

    /**
     * Creates a new agent loop with the given audit logger.
     *
     * @param auditLogger the audit logger for LLM calls
     * @param logWarning  callback for warning messages
     */
    public AgentLoop(AuditLogger auditLogger, Logger logWarning) {
        this.rateLimiter = new LlmRateLimiter(DEFAULT_COOLDOWN_MS);
        this.llmExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "chatae-agent-llm");
            t.setDaemon(true);
            return t;
        });
        this.timeoutMs = new AtomicLong(DEFAULT_TIMEOUT_MS);
        this.reasoningService = new AgentReasoningService(
                logWarning,
                rateLimiter,
                llmExecutor,
                timeoutMs.get(),
                auditLogger
        );

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
     * Update the rate limiter cooldown.
     */
    public void setRateLimitCooldown(long cooldownMs) {
        rateLimiter.setCooldownMillis(cooldownMs);
    }

    /**
     * Update the LLM timeout.
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs.set(timeoutMs);
    }

    /**
     * Run the agent loop for a user message.
     *
     * @param player          The player context
     * @param sessionId       The session ID
     * @param binding         The terminal binding for proposals
     * @param effectiveLocale The locale for LLM responses
     * @param sessionContext  The session context for operations
     * @return The result of the agent loop
     */
    public AgentLoopResult runLoop(AgentPlayerContext player, UUID sessionId, TerminalBinding binding,
                                    String effectiveLocale, AgentSessionContext sessionContext) {
        Map<String, Object> init = new HashMap<>();
        init.put(KEY_PLAYER, player);
        init.put(KEY_SESSION_ID, sessionId);
        init.put(KEY_LOCALE, effectiveLocale);
        init.put(KEY_ITERATION, 0);
        init.put(KEY_DECISION, null);
        init.put(KEY_RESULT, null);
        init.put(KEY_CONTEXT, sessionContext);

        try {
            Optional<LoopState> finalState = graph.invoke(init);
            if (finalState.isEmpty()) {
                return AgentLoopResult.withError("Agent state is empty", 0);
            }

            LoopState state = finalState.get();
            ResultState resultState = state.value(KEY_RESULT)
                    .map(ResultState.class::cast)
                    .orElse(null);

            if (resultState != null) {
                return resultState.toResult();
            }

            int iterations = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
            return AgentLoopResult.withError("No result from agent loop", iterations);
        } catch (Exception e) {
            sessionContext.logError("Agent loop failed", e);
            return AgentLoopResult.withError("Agent loop failed: " + e.getMessage(), 0);
        }
    }

    /**
     * Reason node: Ask the LLM to decide the next action.
     */
    private Map<String, Object> reasonNode(LoopState state) {
        AgentPlayerContext player = state.value(KEY_PLAYER).map(AgentPlayerContext.class::cast).orElse(null);
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        String locale = state.value(KEY_LOCALE).map(String.class::cast).orElse("en_us");
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
        AgentSessionContext ctx = state.value(KEY_CONTEXT).map(AgentSessionContext.class::cast).orElse(null);

        if (player == null || sessionId == null || ctx == null) {
            return Map.of(KEY_RESULT, ResultState.error("Missing player, session, or context", iteration));
        }

        // Check iteration limit
        if (iteration >= MAX_ITERATIONS) {
            return Map.of(KEY_RESULT, ResultState.error("Max iterations reached", iteration));
        }

        // Build conversation history from session
        List<ChatMessage> messages = ctx.getSession(sessionId)
                .map(SessionSnapshot::messages)
                .orElse(List.of());
        String history = ConversationHistoryBuilder.build(messages, MAX_HISTORY_MESSAGES);

        // Render prompt
        Map<String, String> variables = new HashMap<>();
        variables.put("tool_list", TOOL_LIST);
        variables.put("args_schema", ARGS_SCHEMA);
        variables.put("effectiveLocale", locale);
        variables.put("conversation_history", history);

        String prompt = ctx.renderPrompt(PromptId.AGENT_REASON, locale, variables);
        ctx.logDebug("Agent reason iteration={} locale={}", iteration, locale);

        // Call LLM with player ID for rate limiting, locale and iteration for audit
        Optional<AgentDecision> decision = reasoningService.reason(player.getPlayerId(), prompt, locale, iteration);
        if (decision.isEmpty()) {
            return Map.of(KEY_RESULT, ResultState.error("LLM failed to produce a decision", iteration));
        }

        AgentDecision d = decision.get();
        d.thinking().ifPresent(t -> ctx.logDebug("Agent thinking: {}", t));

        return Map.of(
                KEY_DECISION, DecisionState.fromDecision(d),
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

        DecisionState decision = state.value(KEY_DECISION)
                .map(DecisionState.class::cast)
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
        AgentPlayerContext player = state.value(KEY_PLAYER).map(AgentPlayerContext.class::cast).orElse(null);
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
        AgentSessionContext ctx = state.value(KEY_CONTEXT).map(AgentSessionContext.class::cast).orElse(null);

        DecisionState decision = state.value(KEY_DECISION)
                .map(DecisionState.class::cast)
                .orElse(null);

        if (player == null || decision == null || decision.toolCalls == null || decision.toolCalls.isEmpty() || ctx == null) {
            return Map.of(KEY_RESULT, ResultState.error("Invalid state for execution", iteration));
        }

        // Get terminal context from player
        Optional<TerminalContext> terminal = ctx.getTerminal(player);

        for (ToolCall call : decision.toolCalls) {
            ctx.logDebug("Agent executing tool: {} args: {}", call.toolName(), call.argsJson());

            // Execute tool
            ToolOutcome outcome = ctx.executeTool(terminal, call, false);

            // If proposal is needed, return it and exit the loop
            if (outcome.hasProposal()) {
                ctx.logDebug("Agent produced proposal: {}", outcome.proposal().id());
                return Map.of(KEY_RESULT, ResultState.proposal(outcome.proposal(), iteration));
            }

            // Append tool result to session
            if (outcome.result() != null) {
                String payload = ToolMessagePayload.wrap(call, outcome.result());
                if (payload != null && !payload.isBlank()) {
                    ctx.appendMessage(sessionId, new ChatMessage(ChatRole.TOOL, payload, System.currentTimeMillis()));
                }
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
        AgentSessionContext ctx = state.value(KEY_CONTEXT).map(AgentSessionContext.class::cast).orElse(null);

        DecisionState decision = state.value(KEY_DECISION)
                .map(DecisionState.class::cast)
                .orElse(null);

        if (decision == null || decision.response == null || decision.response.isBlank()) {
            return Map.of(KEY_RESULT, ResultState.error("No response in decision", iteration));
        }

        String response = decision.response;

        // Append response to session
        if (sessionId != null && ctx != null) {
            ctx.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, response, System.currentTimeMillis()));
        }

        return Map.of(KEY_RESULT, ResultState.response(response, iteration));
    }

    /**
     * State class for the agent loop.
     */
    private static final class LoopState extends AgentState {
        public LoopState(Map<String, Object> data) {
            super(data);
        }
    }

    private static final class DecisionState implements Serializable {
        private final AgentDecision.AgentAction action;
        private final String thinking;
        private final java.util.List<ToolCall> toolCalls;
        private final String response;

        private DecisionState(AgentDecision.AgentAction action, String thinking, java.util.List<ToolCall> toolCalls, String response) {
            this.action = action;
            this.thinking = thinking;
            this.toolCalls = toolCalls == null ? java.util.List.of() : java.util.List.copyOf(toolCalls);
            this.response = response;
        }

        static DecisionState fromDecision(AgentDecision decision) {
            return new DecisionState(
                    decision.action(),
                    decision.thinking().orElse(null),
                    decision.toolCalls(),
                    decision.response().orElse(null)
            );
        }

        boolean isToolCall() {
            return action == AgentDecision.AgentAction.TOOL_CALL;
        }

        boolean isRespond() {
            return action == AgentDecision.AgentAction.RESPOND;
        }
    }

    private static final class ResultState implements Serializable {
        private final boolean success;
        private final space.controlnet.chatae.core.proposal.Proposal proposal;
        private final String response;
        private final String error;
        private final int iterationsUsed;

        private ResultState(boolean success, space.controlnet.chatae.core.proposal.Proposal proposal, String response, String error, int iterationsUsed) {
            this.success = success;
            this.proposal = proposal;
            this.response = response;
            this.error = error;
            this.iterationsUsed = iterationsUsed;
        }

        static ResultState proposal(space.controlnet.chatae.core.proposal.Proposal proposal, int iterations) {
            return new ResultState(true, proposal, null, null, iterations);
        }

        static ResultState response(String response, int iterations) {
            return new ResultState(true, null, response, null, iterations);
        }

        static ResultState error(String error, int iterations) {
            return new ResultState(false, null, null, error, iterations);
        }

        AgentLoopResult toResult() {
            if (proposal != null) {
                return AgentLoopResult.withProposal(proposal, iterationsUsed);
            }
            if (response != null) {
                return AgentLoopResult.withResponse(response, iterationsUsed);
            }
            if (error != null) {
                return AgentLoopResult.withError(error, iterationsUsed);
            }
            return AgentLoopResult.withError("No result from agent loop", iterationsUsed);
        }
    }
}
