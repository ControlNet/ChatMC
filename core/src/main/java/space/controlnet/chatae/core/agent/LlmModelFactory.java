package space.controlnet.chatae.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.Optional;

public final class LlmModelFactory {
    private LlmModelFactory() {
    }

    public static Optional<ChatModel> build(LlmConfig config) {
        if (config == null) {
            return Optional.empty();
        }
        if (config.provider() != LlmProvider.OPENAI) {
            return Optional.empty();
        }

        String apiKey = resolveApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.model())
                .timeout(config.timeout() == null ? Duration.ofSeconds(5) : config.timeout())
                .maxRetries(config.maxRetries())
                .strictJsonSchema(config.strictJsonSchema());

        config.baseUrl().filter(value -> !value.isBlank()).ifPresent(builder::baseUrl);
        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        config.maxTokens().ifPresent(builder::maxTokens);
        if (config.logRequests()) {
            builder.logRequests(true);
        }
        if (config.logResponses()) {
            builder.logResponses(true);
        }

        return Optional.of(builder.build());
    }

    private static String resolveApiKey(LlmConfig config) {
        if (config.apiKey().isPresent()) {
            return config.apiKey().get();
        }
        if (config.apiKeyEnv().isPresent()) {
            String value = System.getenv(config.apiKeyEnv().get());
            return value == null ? "" : value;
        }
        return "";
    }
}
