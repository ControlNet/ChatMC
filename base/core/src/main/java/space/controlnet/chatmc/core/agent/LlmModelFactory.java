package space.controlnet.chatmc.core.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
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

        return switch (config.provider()) {
            case OPENAI -> buildOpenAi(config);
            case AZURE_OPENAI -> buildAzureOpenAi(config);
            case ANTHROPIC -> buildAnthropic(config);
            case GEMINI -> buildGemini(config);
            case OLLAMA -> buildOllama(config);
        };
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

    private static Optional<ChatModel> buildAzureOpenAi(LlmConfig config) {
        String apiKey = resolveApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String endpoint = config.baseUrl().orElse("");
        String deployment = config.azureDeployment().orElse("");
        if (endpoint.isBlank() || deployment.isBlank()) {
            return Optional.empty();
        }

        var builder = AzureOpenAiChatModel.builder()
                .apiKey(apiKey)
                .endpoint(endpoint)
                .deploymentName(deployment)
                .timeout(config.timeout() == null ? Duration.ofSeconds(5) : config.timeout())
                .maxRetries(config.maxRetries());

        config.azureApiVersion().filter(value -> !value.isBlank()).ifPresent(builder::serviceVersion);
        if (config.logResponses() && !config.logRequests()) {
            builder.logRequestsAndResponses(true);
        }
        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        applyOpenAiMaxTokens(builder, config.maxTokens(), config.model());
        if (config.logRequests()) {
            builder.logRequestsAndResponses(true);
        }

        return Optional.of(builder.build());
    }

    private static Optional<ChatModel> buildAnthropic(LlmConfig config) {
        String apiKey = resolveApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        var builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.model())
                .timeout(config.timeout() == null ? Duration.ofSeconds(5) : config.timeout())
                .maxRetries(config.maxRetries());

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

    private static Optional<ChatModel> buildGemini(LlmConfig config) {
        String apiKey = resolveApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.model())
                .timeout(config.timeout() == null ? Duration.ofSeconds(5) : config.timeout())
                .maxRetries(config.maxRetries());

        config.baseUrl().filter(value -> !value.isBlank()).ifPresent(builder::baseUrl);
        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        config.maxTokens().ifPresent(builder::maxOutputTokens);
        if (config.logRequests() || config.logResponses()) {
            builder.logRequestsAndResponses(true);
        }

        return Optional.of(builder.build());
    }

    private static Optional<ChatModel> buildOllama(LlmConfig config) {
        String baseUrl = config.baseUrl().orElse("");
        if (baseUrl.isBlank()) {
            return Optional.empty();
        }

        var builder = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(config.model())
                .timeout(config.timeout() == null ? Duration.ofSeconds(5) : config.timeout())
                .maxRetries(config.maxRetries());

        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        config.maxTokens().ifPresent(builder::numPredict);
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
