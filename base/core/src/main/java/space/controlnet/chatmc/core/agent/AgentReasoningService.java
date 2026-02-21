package space.controlnet.chatmc.core.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import space.controlnet.chatmc.core.audit.AuditLogger;
import space.controlnet.chatmc.core.audit.LlmAuditEvent;
import space.controlnet.chatmc.core.audit.LlmAuditOutcome;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final int DEFAULT_MAX_TOOL_CALLS = 3;

    private final Logger logger;
    private final LlmRateLimiter rateLimiter;
    private final ExecutorService executor;
    private final java.util.concurrent.atomic.AtomicLong timeoutMs;
    private final AuditLogger auditLogger;
    private final java.util.concurrent.atomic.AtomicInteger maxToolCalls;
    private final java.util.concurrent.atomic.AtomicInteger maxRetries;
    private final java.util.concurrent.atomic.AtomicBoolean logResponses;

    public AgentReasoningService(Logger logger, LlmRateLimiter rateLimiter, ExecutorService executor, long timeoutMs, AuditLogger auditLogger) {
        this.logger = logger;
        this.rateLimiter = rateLimiter;
        this.executor = executor;
        this.timeoutMs = new java.util.concurrent.atomic.AtomicLong(timeoutMs);
        this.auditLogger = auditLogger;
        this.maxToolCalls = new java.util.concurrent.atomic.AtomicInteger(DEFAULT_MAX_TOOL_CALLS);
        this.maxRetries = new java.util.concurrent.atomic.AtomicInteger(0);
        this.logResponses = new java.util.concurrent.atomic.AtomicBoolean(false);
    }

    public void setMaxToolCalls(int maxToolCalls) {
        if (maxToolCalls > 0) {
            this.maxToolCalls.set(maxToolCalls);
        }
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0) {
            this.maxRetries.set(maxRetries);
        }
    }

    public void setTimeoutMs(long timeoutMs) {
        if (timeoutMs > 0) {
            this.timeoutMs.set(timeoutMs);
        }
    }

    public void setLogResponses(boolean logResponses) {
        this.logResponses.set(logResponses);
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

            String content = null;
            int attempts = 0;
            int maxRetryAttempts = Math.max(0, maxRetries.get());

            while (true) {
                long timeout = timeoutMs.get();
                try {
                    if (executor != null && timeout > 0) {
                        Future<String> future = executor.submit(llmCall);
                        try {
                            content = future.get(timeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException e) {
                            future.cancel(true);
                            throw e;
                        }
                    } else {
                        content = llmCall.call();
                    }
                    break;
                } catch (TimeoutException e) {
                    attempts++;
                    if (attempts <= maxRetryAttempts) {
                        logger.warn("LLM call timed out after " + timeout + "ms, retrying (" + attempts + "/" + maxRetryAttempts + ")", null);
                        sleepBackoff(attempts);
                        continue;
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    logger.warn("LLM call timed out after " + timeout + "ms", null);
                    logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.TIMEOUT, "Timeout after " + timeout + "ms");
                    return Optional.empty();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    attempts++;
                    if (attempts <= maxRetryAttempts && isRetryable(cause)) {
                        logger.warn("LLM call failed, retrying (" + attempts + "/" + maxRetryAttempts + ")", cause);
                        sleepBackoff(attempts);
                        continue;
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    logger.warn("LLM call failed", cause);
                    logLlmAudit(playerIdStr, locale, iteration, duration, LlmAuditOutcome.ERROR,
                            cause != null ? cause.getMessage() : "Unknown error");
                    return Optional.empty();
                }
            }

            if (logResponses.get()) {
                logger.debug("LLM raw output: " + content);
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
            String json = stripCodeBlocks(content);
            List<JsonObject> objects = extractDecisionObjects(json);
            if (objects.isEmpty()) {
                logger.warn("Invalid agent decision: no JSON object found", null);
                return Optional.empty();
            }

            String thinking = null;
            String response = null;
            List<ToolCall> toolCalls = new ArrayList<>();

            for (JsonObject obj : objects) {
                if (obj == null) {
                    continue;
                }

                if (thinking == null && obj.has("thinking") && !obj.get("thinking").isJsonNull()) {
                    thinking = obj.get("thinking").getAsString();
                }

                String responseMessage = appendToolCalls(obj, toolCalls);
                if (responseMessage != null) {
                    response = responseMessage;
                    break;
                }
            }

            if (response != null) {
                return Optional.of(AgentDecision.respond(response, thinking));
            }

            if (!toolCalls.isEmpty()) {
                int limit = maxToolCalls.get();
                if (toolCalls.size() > limit) {
                    logger.warn("LLM returned " + toolCalls.size() + " tool calls; truncating to " + limit, null);
                    toolCalls = new ArrayList<>(toolCalls.subList(0, limit));
                }
                return Optional.of(AgentDecision.toolCalls(toolCalls, thinking));
            }

            logger.warn("Invalid agent decision: missing tool calls", null);
            return Optional.empty();
        } catch (ToolCallArgsParseBoundary.ToolCallParseBoundaryException e) {
            logger.warn(e.getMessage(), null);
            return Optional.empty();
        } catch (Exception e) {
            String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            logger.warn("Failed to parse agent decision JSON: " + preview, e);
            return Optional.empty();
        }
    }

    private static String stripCodeBlocks(String content) {
        String json = content.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }

    private static List<JsonObject> extractDecisionObjects(String text) {
        List<JsonObject> objects = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return objects;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            JsonElement element = parseLenientElement(trimmed);
            if (element != null && element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item != null && item.isJsonObject()) {
                        objects.add(item.getAsJsonObject());
                    }
                }
            }
            return objects;
        }

        for (String objText : extractJsonObjects(trimmed)) {
            JsonObject obj = parseLenientObject(objText);
            if (obj != null) {
                objects.add(obj);
            }
        }
        return objects;
    }

    private static List<String> extractJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        if (text == null) {
            return objects;
        }
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(text.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return objects;
    }

    private static JsonObject parseLenientObject(String json) {
        try (StringReader reader = new StringReader(json)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            return GSON.fromJson(jsonReader, JsonObject.class);
        }
    }

    private static JsonElement parseLenientElement(String json) {
        try (StringReader reader = new StringReader(json)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            return GSON.fromJson(jsonReader, JsonElement.class);
        }
    }

    private static boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.SocketException
                    || current instanceof java.io.IOException) {
                return true;
            }

            String name = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains("ratelimit") || name.contains("too_many") || name.contains("toomany") || name.contains("throttle")) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("rate limit")
                        || lower.contains("too many requests")
                        || lower.contains("429")
                        || lower.contains("timeout")
                        || lower.contains("timed out")
                        || lower.contains("connection")
                        || lower.contains("connect")
                        || lower.contains("refused")
                        || lower.contains("unavailable")
                        || lower.contains("503")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private static void sleepBackoff(int attempt) {
        long base = 200L;
        long delay = Math.min(base * (1L << Math.min(attempt - 1, 4)), 2000L);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String appendToolCalls(JsonObject obj, List<ToolCall> toolCalls) {
        JsonArray batch = null;
        if (obj.has("tool_calls") && obj.get("tool_calls").isJsonArray()) {
            batch = obj.getAsJsonArray("tool_calls");
        } else if (obj.has("tools") && obj.get("tools").isJsonArray()) {
            batch = obj.getAsJsonArray("tools");
        }

        if (batch != null) {
            for (JsonElement element : batch) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("tool") || !entry.has("args")) {
                    continue;
                }
                String tool = entry.get("tool").getAsString();
                if ("response".equals(tool)) {
                    String message = extractResponseMessage(entry.get("args"));
                    if (message == null) {
                        logger.warn("Invalid response tool: missing message", null);
                        return null;
                    }
                    return message;
                }
                String argsJson = entry.get("args").toString();
                ToolCallArgsParseBoundary.validate(tool, argsJson);
                toolCalls.add(new ToolCall(tool, argsJson));
            }
            return null;
        }

        if (!obj.has("tool")) {
            return null;
        }
        String tool = obj.get("tool").getAsString();
        if ("response".equals(tool)) {
            String message = extractResponseMessage(obj.get("args"));
            if (message == null) {
                logger.warn("Invalid response tool: missing message", null);
                return null;
            }
            return message;
        }
        if (!obj.has("args")) {
            logger.warn("Invalid tool call: missing 'args'", null);
            return null;
        }
        String argsJson = obj.get("args").toString();
        ToolCallArgsParseBoundary.validate(tool, argsJson);
        toolCalls.add(new ToolCall(tool, argsJson));
        return null;
    }

    private static String extractResponseMessage(JsonElement argsElement) {
        if (argsElement == null || argsElement.isJsonNull()) {
            return null;
        }
        if (argsElement.isJsonPrimitive()) {
            try {
                return argsElement.getAsString();
            } catch (Exception ignored) {
                return argsElement.toString();
            }
        }
        if (argsElement.isJsonObject()) {
            JsonObject obj = argsElement.getAsJsonObject();
            if (obj.has("message") && !obj.get("message").isJsonNull()) {
                return obj.get("message").getAsString();
            }
        }
        return null;
    }
}
