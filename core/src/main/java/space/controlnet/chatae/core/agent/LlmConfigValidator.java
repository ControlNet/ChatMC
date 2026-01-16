package space.controlnet.chatae.core.agent;

import java.util.ArrayList;
import java.util.List;

public final class LlmConfigValidator {
    private LlmConfigValidator() {
    }

    public static List<String> validate(LlmConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("LLM config is missing.");
            return errors;
        }
        if (config.model() == null || config.model().isBlank()) {
            errors.add("model is required.");
        }
        switch (config.provider()) {
            case OPENAI, ANTHROPIC, GEMINI -> {
                if (!hasApiKey(config)) {
                    errors.add(config.provider().name() + " requires apiKey or apiKeyEnv.");
                }
            }
            case AZURE_OPENAI -> {
                if (!hasApiKey(config)) {
                    errors.add("AZURE_OPENAI requires apiKey or apiKeyEnv.");
                }
                if (config.azureEndpoint().isEmpty() || config.azureEndpoint().get().isBlank()) {
                    errors.add("AZURE_OPENAI requires azureEndpoint.");
                }
                if (config.azureDeployment().isEmpty() || config.azureDeployment().get().isBlank()) {
                    errors.add("AZURE_OPENAI requires azureDeployment.");
                }
            }
            case OLLAMA -> {
                if (config.baseUrl().isEmpty() || config.baseUrl().get().isBlank()) {
                    errors.add("OLLAMA requires baseUrl.");
                }
            }
        }
        if (config.timeout() == null || config.timeout().isNegative() || config.timeout().isZero()) {
            errors.add("timeoutSeconds must be > 0.");
        }
        if (config.maxToolCalls() <= 0) {
            errors.add("maxToolCalls must be > 0.");
        }
        if (config.maxIterations() <= 0) {
            errors.add("maxIterations must be > 0.");
        }
        if (config.maxHistoryMessages() <= 0) {
            errors.add("maxHistoryMessages must be > 0.");
        }
        return errors;
    }

    private static boolean hasApiKey(LlmConfig config) {
        return config.apiKey().filter(value -> !value.isBlank()).isPresent()
                || config.apiKeyEnv().filter(value -> !value.isBlank()).isPresent();
    }
}
