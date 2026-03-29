package space.controlnet.chatmc.core.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import space.controlnet.chatmc.core.audit.AuditLogger;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolMessagePayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core agent loop implementation using LangGraph4j.
 * This class contains all the AI agent logic without MC dependencies.
 */
public final class AgentLoop {
    private static final int DEFAULT_MAX_ITERATIONS = 20;
    private static final int DEFAULT_GRAPH_RECURSION_LIMIT = 256;
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    private static final long DEFAULT_COOLDOWN_MS = 1500;

    // State keys
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_LOCALE = "locale";
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_DECISION_ACTION = "decisionAction";
    private static final String KEY_RESULT_PROPOSAL = "resultProposal";
    private static final String KEY_RESULT_RESPONSE = "resultResponse";
    private static final String KEY_RESULT_ERROR = "resultError";
    private final CompiledGraph<LoopState> graph;
    private final AgentReasoningService reasoningService;
    private final LlmRateLimiter rateLimiter;
    private final ExecutorService llmExecutor;
    private final AtomicLong timeoutMs;
    private final java.util.concurrent.atomic.AtomicInteger maxIterations;
    private final ConcurrentHashMap<UUID, AgentSessionContext> activeContexts;
    private final ConcurrentHashMap<UUID, AgentPlayerContext> activePlayers;
    private final ConcurrentHashMap<UUID, AgentDecision> activeDecisions;
    private final ConcurrentHashMap<UUID, space.controlnet.chatmc.core.proposal.Proposal> activeProposals;

    /**
     * Creates a new agent loop with the given audit logger.
     *
     * @param auditLogger the audit logger for LLM calls
     * @param logWarning  callback for warning messages
     */
    public AgentLoop(AuditLogger auditLogger, Logger logWarning) {
        this.rateLimiter = new LlmRateLimiter(DEFAULT_COOLDOWN_MS);
        this.llmExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "chatmc-agent-llm");
            t.setDaemon(true);
            return t;
        });
        this.timeoutMs = new AtomicLong(DEFAULT_TIMEOUT_MS);
        this.maxIterations = new java.util.concurrent.atomic.AtomicInteger(DEFAULT_MAX_ITERATIONS);
        this.activeContexts = new ConcurrentHashMap<>();
        this.activePlayers = new ConcurrentHashMap<>();
        this.activeDecisions = new ConcurrentHashMap<>();
        this.activeProposals = new ConcurrentHashMap<>();
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
                    .recursionLimit(DEFAULT_GRAPH_RECURSION_LIMIT)
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
        reasoningService.setTimeoutMs(timeoutMs);
    }

    public void setMaxToolCalls(int maxToolCalls) {
        reasoningService.setMaxToolCalls(maxToolCalls);
    }

    public void setMaxIterations(int maxIterations) {
        if (maxIterations > 0) {
            this.maxIterations.set(maxIterations);
        }
    }

    public void setLogResponses(boolean logResponses) {
        reasoningService.setLogResponses(logResponses);
    }

    public void setMaxRetries(int maxRetries) {
        reasoningService.setMaxRetries(maxRetries);
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
        init.put(KEY_SESSION_ID, sessionId);
        init.put(KEY_LOCALE, effectiveLocale);
        init.put(KEY_ITERATION, 0);
        init.put(KEY_DECISION_ACTION, null);
        init.put(KEY_RESULT_PROPOSAL, null);
        init.put(KEY_RESULT_RESPONSE, null);
        init.put(KEY_RESULT_ERROR, null);

        try {
            activePlayers.put(sessionId, player);
            activeContexts.put(sessionId, sessionContext);
            Optional<LoopState> finalState = graph.invoke(init);
            if (finalState.isEmpty()) {
                return AgentLoopResult.withError("Agent state is empty", 0);
            }

            LoopState state = finalState.get();
            int iterations = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
            boolean hasProposal = state.value(KEY_RESULT_PROPOSAL)
                    .map(Boolean.class::cast)
                    .orElse(Boolean.FALSE);
            if (hasProposal) {
                space.controlnet.chatmc.core.proposal.Proposal proposal = activeProposals.remove(sessionId);
                if (proposal != null) {
                    return AgentLoopResult.withProposal(proposal, iterations);
                }
                return AgentLoopResult.withError("No proposal from agent loop", iterations);
            }

            String response = state.value(KEY_RESULT_RESPONSE)
                    .map(String.class::cast)
                    .orElse(null);
            if (response != null && !response.isBlank()) {
                return AgentLoopResult.withResponse(response, iterations);
            }

            String error = state.value(KEY_RESULT_ERROR)
                    .map(String.class::cast)
                    .orElse(null);
            if (error != null && !error.isBlank()) {
                return AgentLoopResult.withError(error, iterations);
            }

            return AgentLoopResult.withError("No result from agent loop", iterations);
        } catch (Exception e) {
            sessionContext.logError("Agent loop failed", e);
            return AgentLoopResult.withError("Agent loop failed: " + e.getMessage(), 0);
        } finally {
            activeDecisions.remove(sessionId);
            activeProposals.remove(sessionId);
            activePlayers.remove(sessionId);
            activeContexts.remove(sessionId);
        }
    }

    /**
     * Reason node: Ask the LLM to decide the next action.
     */
    private Map<String, Object> reasonNode(LoopState state) {
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        String locale = state.value(KEY_LOCALE).map(String.class::cast).orElse("en_us");
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
        AgentPlayerContext player = sessionId == null ? null : activePlayers.get(sessionId);
        AgentSessionContext ctx = sessionId == null ? null : activeContexts.get(sessionId);

        if (player == null || sessionId == null || ctx == null) {
            return errorResult("Missing player, session, or context", iteration);
        }

        // Check iteration limit
        if (iteration >= maxIterations.get()) {
            return errorResult("Max iterations reached", iteration);
        }

        // Build conversation history from session
        List<ChatMessage> messages = ctx.getSession(sessionId)
                .map(SessionSnapshot::messages)
                .orElse(List.of());
        String history = ConversationHistoryBuilder.build(messages);

        // Render prompt
        List<AgentTool> tools = ctx.getToolSpecs();
        ToolPrompt promptData = buildToolPrompt(tools);
        Map<String, String> variables = new HashMap<>();
        variables.put("tool_list", promptData.toolList());
        variables.put("args_schema", promptData.argsSchema());
        variables.put("tools_section", promptData.toolsSection());
        variables.put("effectiveLocale", locale);
        variables.put("conversation_history", history);

        String prompt = ctx.renderPrompt(PromptId.AGENT_REASON, locale, variables);
        ctx.logDebug("Agent reason iteration={} locale={}", iteration, locale);

        // Call LLM with player ID for rate limiting, locale and iteration for audit
        Optional<AgentDecision> decision = reasoningService.reason(player.getPlayerId(), prompt, locale, iteration);
        if (decision.isEmpty()) {
            return errorResult("LLM failed to produce a decision", iteration);
        }

        AgentDecision d = decision.get();
        d.thinking().ifPresent(t -> ctx.logDebug("Agent thinking: {}", t));
        activeDecisions.put(sessionId, d);

        return decisionUpdate(d.action(), iteration + 1);
    }

    private static ToolPrompt buildToolPrompt(List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ToolPrompt("", "", "");
        }

        String toolList = tools.stream()
                .map(AgentTool::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        String argsSchema = tools.stream()
                .map(AgentLoop::buildArgsSchemaLine)
                .filter(line -> !line.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String toolsSection = tools.stream()
                .map(AgentLoop::buildToolSection)
                .filter(section -> !section.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        return new ToolPrompt(toolList, argsSchema, toolsSection);
    }

    private static String buildArgsSchemaLine(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        return tool.argsSchemaOptional()
                .map(schema -> "- " + tool.name() + ": " + schema)
                .orElse("");
    }

    private static String buildSection(String header, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(header);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            builder.append("\n  - ").append(line);
        }
        return builder.toString();
    }

    private static String buildReturnSection(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        Optional<String> schema = tool.resultSchemaOptional();
        List<String> details = tool.resultDescriptionOptional();
        if (schema.isEmpty() && details.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Return Details:");
        if (schema.isPresent()) {
            builder.append("\n  - ").append(schema.get());
        }
        if (!details.isEmpty()) {
            for (String line : details) {
                builder.append("\n  - ").append(line);
            }
        }
        return builder.toString();
    }

    private record ToolPrompt(
            String toolList,
            String argsSchema,
            String toolsSection
    ) {
    }

    private static String buildToolSection(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        List<String> sections = new java.util.ArrayList<>();
        sections.add("### " + tool.name());
        tool.descriptionOptional().ifPresent(description -> sections.add("Description:\n" + description));
        tool.argsSchemaOptional().ifPresent(schema -> sections.add("Arguments Schema:\n" + schema));

        String argsDetails = buildSection("Arguments Details:", tool.argsDescriptionOptional());
        if (!argsDetails.isBlank()) {
            sections.add(argsDetails);
        }
        String returns = buildReturnSection(tool);
        if (!returns.isBlank()) {
            sections.add(returns);
        }
        String examples = buildSection("Examples:", tool.examplesOptional());
        if (!examples.isBlank()) {
            sections.add(examples);
        }
        return String.join("\n\n", sections).trim();
    }

    /**
     * Route after reason: decide whether to execute a tool, respond, or end.
     */
    private String routeAfterReason(LoopState state) {
        // Check if we have a result (error case)
        if (hasTerminalResult(state)) {
            return "end";
        }

        String action = state.value(KEY_DECISION_ACTION)
                .map(String.class::cast)
                .orElse(null);

        if (action == null || action.isBlank()) {
            return "end";
        }

        if (AgentDecision.AgentAction.TOOL_CALL.name().equals(action)) {
            return "execute";
        } else if (AgentDecision.AgentAction.RESPOND.name().equals(action)) {
            return "respond";
        }

        return "end";
    }

    /**
     * Execute node: Execute the tool call.
     */
    private Map<String, Object> executeNode(LoopState state) {
        UUID sessionId = state.value(KEY_SESSION_ID).map(UUID.class::cast).orElse(null);
        int iteration = state.value(KEY_ITERATION).map(Integer.class::cast).orElse(0);
        AgentPlayerContext player = sessionId == null ? null : activePlayers.get(sessionId);
        AgentSessionContext ctx = sessionId == null ? null : activeContexts.get(sessionId);
        AgentDecision decision = sessionId == null ? null : activeDecisions.get(sessionId);

        List<ToolCall> toolCalls = decision == null ? List.of() : decision.toolCalls();
        String thinking = decision == null ? null : decision.thinking().orElse(null);

        if (player == null || toolCalls.isEmpty() || ctx == null) {
            return errorResult("Invalid state for execution", iteration);
        }

        // Get terminal context from player
        Optional<TerminalContext> terminal = ctx.getTerminal(player);

        boolean firstCall = true;
        for (ToolCall call : toolCalls) {
            ctx.logDebug("Agent executing tool: {} args: {}", call.toolName(), call.argsJson());

            // Execute tool
            ToolOutcome outcome = ctx.executeTool(terminal, call, false);

            // If proposal is needed, return it and exit the loop
            if (outcome.hasProposal()) {
                ctx.logDebug("Agent produced proposal: {}", outcome.proposal().id());
                activeProposals.put(sessionId, outcome.proposal());
                return proposalResult();
            }

            // Append tool result to session
            if (outcome.result() != null) {
                String payload = ToolMessagePayload.wrap(call, outcome.result(), firstCall ? thinking : null);
                if (payload != null && !payload.isBlank()) {
                    ctx.appendMessage(sessionId, new ChatMessage(ChatRole.TOOL, payload, System.currentTimeMillis()));
                }
            }
            firstCall = false;
        }

        // Continue the loop
        return Map.of(KEY_ITERATION, iteration);
    }

    /**
     * Route after execute: decide whether to continue reasoning or end.
     */
    private String routeAfterExecute(LoopState state) {
        // Check if we have a result (proposal case)
        if (hasTerminalResult(state)) {
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
        AgentSessionContext ctx = sessionId == null ? null : activeContexts.get(sessionId);
        AgentDecision decision = sessionId == null ? null : activeDecisions.get(sessionId);

        String response = decision == null ? null : decision.response().orElse(null);

        if (response == null || response.isBlank()) {
            return errorResult("No response in decision", iteration);
        }

        // Append response to session
        if (sessionId != null && ctx != null) {
            ctx.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, response, System.currentTimeMillis()));
        }

        return responseResult(response);
    }

    private static boolean hasTerminalResult(LoopState state) {
        return state.value(KEY_RESULT_PROPOSAL).isPresent()
                || state.value(KEY_RESULT_RESPONSE).isPresent()
                || state.value(KEY_RESULT_ERROR).isPresent();
    }

    private static Map<String, Object> errorResult(String error, int iterations) {
        return Map.of(
                KEY_RESULT_ERROR, error,
                KEY_ITERATION, iterations
        );
    }

    private static Map<String, Object> decisionUpdate(AgentDecision.AgentAction action, int iteration) {
        Map<String, Object> update = new HashMap<>();
        update.put(KEY_DECISION_ACTION, action.name());
        update.put(KEY_ITERATION, iteration);
        return update;
    }

    private static Map<String, Object> proposalResult() {
        return Map.of(KEY_RESULT_PROPOSAL, Boolean.TRUE);
    }

    private static Map<String, Object> responseResult(String response) {
        return Map.of(KEY_RESULT_RESPONSE, response);
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
