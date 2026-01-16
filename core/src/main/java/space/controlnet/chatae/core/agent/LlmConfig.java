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
        long cooldownMillis,
        int maxToolCalls,
        int maxIterations,
        int maxHistoryMessages,
        boolean strictJsonSchema,
        boolean logRequests,
        boolean logResponses,
        Optional<String> azureDeployment,
        Optional<String> azureApiVersion
) {
    public static LlmConfig defaults() {
        return new LlmConfig(
                LlmProvider.OPENAI,
                "gpt-5-nano",
                Optional.empty(),
                Optional.empty(),
                Optional.of("CHATAE_OPENAI_API_KEY"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(180),
                10,
                0L,
                100,
                200,
                1000,
                true,
                false,
                false,
                Optional.empty(),
                Optional.empty()
        );
    }
}
