package space.controlnet.chatae.core.agent;

import java.time.Duration;
import java.util.Optional;

public record LlmConfig(
        LlmProvider provider,
        String model,
        Optional<String> baseUrl,
        Optional<String> apiKey,
        Optional<String> apiKeyEnv,
        Optional<Double> temperature,
        Optional<Double> topP,
        Optional<Integer> maxTokens,
        Duration timeout,
        int maxRetries,
        boolean strictJsonSchema,
        boolean logRequests,
        boolean logResponses
) {
    public static LlmConfig defaults() {
        return new LlmConfig(
                LlmProvider.OPENAI,
                "gpt-4o-mini",
                Optional.empty(),
                Optional.empty(),
                Optional.of("CHATAE_OPENAI_API_KEY"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(5),
                2,
                true,
                false,
                false
        );
    }
}
