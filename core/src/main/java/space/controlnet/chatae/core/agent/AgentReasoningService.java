package space.controlnet.chatae.core.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import space.controlnet.chatae.core.audit.AuditLogger;
import space.controlnet.chatae.core.audit.LlmAuditEvent;
import space.controlnet.chatae.core.audit.LlmAuditOutcome;
import space.controlnet.chatae.core.tools.ToolCall;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service that asks the LLM to decide the next action in the agent loop.
 * Returns an AgentDecision with either a tool call or a direct response.
 */
public final class AgentReasoningService {
    private static final Gson GSON = new Gson();

    private final Logger logger;
    private final LlmRateLimiter rateLimiter;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final AuditLogger auditLogger;

    public AgentReasoningService(Logger logger, LlmRateLimiter rateLimiter, ExecutorService executor, long timeoutMs, AuditLogger auditLogger) {
        this.logger = logger;
        this.rateLimiter = rateLimiter;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.auditLogger = auditLogger;
    }

    /**
     * Given a rendered prompt, ask the LLM to decide the next action.
     *
     * @param playerId The player ID for rate limiting
     * @param prompt The fully rendered prompt including conversation history
     * @param locale The locale used for the prompt
     * @param iteration The current iteration number
     * @return AgentDecision with either tool_call or respond action
     */
    public Optional<AgentDecision> reason(UUID playerId, String prompt, String locale, int iteration) {
        long startTime = System.currentTimeMillis();
        String playerIdStr = playerId != null ? playerId.toString() : "unknown";

        // Check rate limit
        if (rateLimiter != null && playerId != null && !rateLimiter.allow(playerId)) {
            logger.warn("Rate limit exceeded for player " + playerId, null);
            logLlmAudit(playerIdStr, locale, iteration, 0, LlmAuditOutcome.RATE_LIMITED, "Rate limit exceeded");
            return Optional.empty();
        }

        Optional<ChatModel> modelOpt = LlmRuntime.model();
        if (modelOpt.isEmpty()) {
            logger.warn("LLM model not available for reasoning", null);
            logLlmAudit(playerIdStr, locale, iteration, 0, LlmAuditOutcome.ERROR, "LLM model not available");
            return Optional.empty();
        }

        try {
            ChatModel model = modelOpt.get();
            Callable<String> llmCall = () -> {
                ChatRequest request = ChatRequest.builder()
                        .messages(java.util.List.of(new UserMessage(prompt)))
                        .build();
                var response = model.chat(request);
                return response.aiMessage().text();
            };

            String content;
            if (executor != null && timeoutMs > 0) {
                // Execute with timeout
                Future<String> future = executor.submit(llmCall);
                try {
                    content = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    long duration = System.currentTimeMillis() - startTime;
                    logger.warn("LLM call timed out after " + timeoutMs + "ms", null);
                    logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.TIMEOUT, "Timeout after " + timeoutMs + "ms");
                    return Optional.empty();
                } catch (ExecutionException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.warn("LLM call failed", e.getCause());
                    logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.ERROR, e.getCause() != null ? e.getCause().getMessage() : "Unknown error");
                    return Optional.empty();
                }
            } else {
                // Execute directly without timeout
                content = llmCall.call();
            }

            long duration = System.currentTimeMillis() - startTime;
            Optional<AgentDecision> decision = parseDecision(content);

            if (decision.isEmpty()) {
                logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.PARSE_ERROR, "Failed to parse LLM response");
            } else {
                logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.SUCCESS, null);
            }

            return decision;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("Agent reasoning failed", e);
            logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.ERROR, e.getMessage());
            return Optional.empty();
        }
    }

    private void logLlmAudit(String playerId, String locale, int iteration, long durationMillis, LlmAuditOutcome outcome, String error) {
        if (auditLogger != null) {
            auditLogger.logLlm(new LlmAuditEvent(
                    playerId,
                    System.currentTimeMillis(),
                    "agent.reason",
                    locale,
                    iteration,
                    durationMillis,
                    outcome,
                    error
            ));
        }
    }

    /**
     * Parse the LLM's JSON response into an AgentDecision.
     */
    private Optional<AgentDecision> parseDecision(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        try {
            // Strip markdown code blocks if present
            String json = content.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            } else if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();

            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("action")) {
                logger.warn("Invalid agent decision: missing 'action' field", null);
                return Optional.empty();
            }

            String action = obj.get("action").getAsString();
            String thinking = obj.has("thinking") && !obj.get("thinking").isJsonNull()
                    ? obj.get("thinking").getAsString()
                    : null;

            if ("tool_call".equals(action)) {
                if (!obj.has("tool") || !obj.has("args")) {
                    logger.warn("Invalid tool_call decision: missing 'tool' or 'args'", null);
                    return Optional.empty();
                }
                String tool = obj.get("tool").getAsString();
                String argsJson = obj.get("args").toString();
                return Optional.of(AgentDecision.toolCall(new ToolCall(tool, argsJson), thinking));
            } else if ("respond".equals(action)) {
                if (!obj.has("response")) {
                    logger.warn("Invalid respond decision: missing 'response'", null);
                    return Optional.empty();
                }
                String response = obj.get("response").getAsString();
                return Optional.of(AgentDecision.respond(response, thinking));
            } else {
                logger.warn("Unknown action type: " + action, null);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse agent decision JSON: " + content, e);
            return Optional.empty();
        }
    }
}
