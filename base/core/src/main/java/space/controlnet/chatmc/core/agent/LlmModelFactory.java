package space.controlnet.chatmc.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class LlmModelFactory {
    private LlmModelFactory() {
    }

    public static Optional<ChatModel> build(LlmConfig config) {
        if (config == null) {
            return Optional.empty();
        }

        return buildOpenAi(config);
    }

    private static Optional<ChatModel> buildOpenAi(LlmConfig config) {
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
        applyOpenAiMaxTokens(builder, config.maxTokens(), config.model());
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

    private static void applyOpenAiMaxTokens(Object builder, Optional<Integer> maxTokens, String modelName) {
        if (builder == null || maxTokens == null || maxTokens.isEmpty()) {
            return;
        }
        int value = maxTokens.get();
        if (invokeIntSetter(builder, "maxCompletionTokens", value)) {
            return;
        }
        if (isGpt5Model(modelName)) {
            return;
        }
        invokeIntSetter(builder, "maxTokens", value);
    }

    private static boolean isGpt5Model(String modelName) {
        if (modelName == null) {
            return false;
        }
        return modelName.toLowerCase(Locale.ROOT).contains("gpt-5");
    }

    private static boolean invokeIntSetter(Object target, String methodName, int value) {
        try {
            Method method = target.getClass().getMethod(methodName, int.class);
            method.invoke(target, value);
            return true;
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = target.getClass().getMethod(methodName, Integer.class);
                method.invoke(target, value);
                return true;
            } catch (NoSuchMethodException ignored2) {
                return false;
            } catch (Exception ignored2) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
