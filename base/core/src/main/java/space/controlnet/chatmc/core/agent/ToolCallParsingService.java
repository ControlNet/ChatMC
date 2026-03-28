package space.controlnet.chatmc.core.agent;

import space.controlnet.chatmc.core.tools.LocalCommandParser;
import space.controlnet.chatmc.core.tools.ParseOutcome;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ToolCallParsingService {
    private ToolCallParsingService() {
    }

    public static CompletableFuture<ParseOutcome> parseAsync(UUID playerId,
                                                             String prompt,
                                                             ReflectiveToolCallParser llmParser,
                                                             Executor executor,
                                                             long timeoutMs,
                                                             LlmRateLimiter rateLimiter) {
        String rendered = prompt == null ? "" : prompt.trim();
        if (rendered.isEmpty()) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "empty", "Empty command"));
        }

        String input = rendered;
        String marker = "\nUser: ";
        int idx = rendered.lastIndexOf(marker);
        if (idx >= 0) {
            input = rendered.substring(idx + marker.length()).trim();
        }

        ToolCall local = LocalCommandParser.parse(input);
        if (local != null) {
            return CompletableFuture.completedFuture(new ParseOutcome(local, null, null));
        }

        if (!llmParser.isAvailable()) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "llm_unavailable", "LLM unavailable"));
        }

        if (!rateLimiter.allow(playerId)) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "llm_rate_limited", "LLM rate limit exceeded"));
        }

        return CompletableFuture.supplyAsync(() -> llmParser.parse(rendered).orElse(null), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((toolCall, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        if (cause instanceof java.util.concurrent.TimeoutException) {
                            return new ParseOutcome(null, "llm_timeout", "LLM request timed out");
                        }
                        return new ParseOutcome(null, "llm_failed", "LLM request failed");
                    }
                    if (toolCall == null) {
                        return new ParseOutcome(null, "unknown_command", "Unknown command");
                    }
                    return new ParseOutcome(toolCall, null, null);
                });
    }
}
