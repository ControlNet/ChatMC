package space.controlnet.chatmc.core.agent;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.Reader;
import java.io.Writer;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class LlmConfigParser {
    private LlmConfigParser() {
    }

    public static LlmConfig parse(Reader reader, LlmConfig defaults) {
        if (reader == null) {
            return defaults;
        }
        try {
            Config root = new TomlParser().parse(reader);
            return parseFromToml(root, defaults);
        } catch (Exception e) {
            return defaults;
        }
    }

    public static void writeToml(Writer writer, LlmConfig config) {
        try {
            writer.write(toTomlString(config));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write LLM TOML config", e);
        }
    }

    private static String toTomlString(LlmConfig config) {
        StringBuilder builder = new StringBuilder();
        appendHeader(builder);
        appendProvider(builder, config);
        appendString(builder, "model", config.model(), "Model identifier.");
        appendOptionalString(builder, "baseUrl", config.baseUrl(),
                "Optional OpenAI-compatible endpoint override for the OpenAI client.");
        appendOptionalString(builder, "apiKey", config.apiKey(),
                "Optional API key value (avoid committing secrets).");
        appendOptionalString(builder, "apiKeyEnv", config.apiKeyEnv(),
                "Optional environment variable name that stores the API key.");
        appendOptionalDouble(builder, "temperature", config.temperature(),
                "Optional sampling temperature (0.0 to 2.0).");
        appendOptionalDouble(builder, "topP", config.topP(),
                "Optional nucleus sampling parameter (0.0 to 1.0).");
        appendOptionalInt(builder, "maxTokens", config.maxTokens(),
                "Optional maximum output tokens per request.");
        appendLong(builder, "timeoutSeconds", config.timeout().toSeconds(),
                "LLM call timeout in seconds.");
        appendInt(builder, "maxRetries", config.maxRetries(),
                "Maximum retry attempts for LLM calls.");
        appendLong(builder, "cooldownMillis", config.cooldownMillis(),
                "Minimum cooldown between LLM calls per player (ms).");
        appendInt(builder, "maxToolCalls", config.maxToolCalls(),
                "Maximum tool calls accepted per agent decision.");
        appendInt(builder, "maxIterations", config.maxIterations(),
                "Maximum agent loop iterations per request.");
        appendBoolean(builder, "strictJsonSchema", config.strictJsonSchema(),
                "Require strict JSON schema adherence in tool calls.");
        appendBoolean(builder, "logRequests", config.logRequests(),
                "Log outbound LLM requests (avoid leaking secrets).");
        appendBoolean(builder, "logResponses", config.logResponses(),
                "Log LLM responses for debugging.");
        return builder.toString();
    }

    private static void appendHeader(StringBuilder builder) {
        builder.append("# ChatMC LLM configuration").append(System.lineSeparator());
        builder.append("# Lines starting with # are comments.").append(System.lineSeparator());
        builder.append(System.lineSeparator());
    }

    private static void appendProvider(StringBuilder builder, LlmConfig config) {
        builder.append("# Only supported LLM provider. Keep this set to OPENAI.")
                .append(System.lineSeparator());
        appendString(builder, "provider", config.provider().name(), null);
    }

    private static void appendString(StringBuilder builder, String key, String value, String comment) {
        appendComment(builder, comment);
        builder.append(key).append(" = \"").append(escapeString(value)).append("\"")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static void appendOptionalString(StringBuilder builder, String key, Optional<String> value, String comment) {
        appendComment(builder, comment);
        if (value.isPresent()) {
            builder.append(key).append(" = \"").append(escapeString(value.get())).append("\"");
        } else {
            builder.append("#").append(key).append(" = \"\"");
        }
        builder.append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendOptionalDouble(StringBuilder builder, String key, Optional<Double> value, String comment) {
        appendComment(builder, comment);
        if (value.isPresent()) {
            builder.append(key).append(" = ").append(value.get());
        } else {
            builder.append("#").append(key).append(" = 0.0");
        }
        builder.append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendOptionalInt(StringBuilder builder, String key, Optional<Integer> value, String comment) {
        appendComment(builder, comment);
        if (value.isPresent()) {
            builder.append(key).append(" = ").append(value.get());
        } else {
            builder.append("#").append(key).append(" = 0");
        }
        builder.append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendInt(StringBuilder builder, String key, int value, String comment) {
        appendComment(builder, comment);
        builder.append(key).append(" = ").append(value)
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static void appendLong(StringBuilder builder, String key, long value, String comment) {
        appendComment(builder, comment);
        builder.append(key).append(" = ").append(value)
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static void appendBoolean(StringBuilder builder, String key, boolean value, String comment) {
        appendComment(builder, comment);
        builder.append(key).append(" = ").append(value)
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static void appendComment(StringBuilder builder, String comment) {
        if (comment == null || comment.isBlank()) {
            return;
        }
        builder.append("# ").append(comment.trim()).append(System.lineSeparator());
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static LlmConfig parseFromToml(Config root, LlmConfig defaults) {
        if (root == null) {
            return defaults;
        }

        LlmProvider provider = parseProvider(readString(root, "provider", defaults.provider().name()), defaults.provider());
        String model = readString(root, "model", defaults.model());
        Optional<String> baseUrl = readOptionalString(root, "baseUrl");
        Optional<String> apiKey = readOptionalString(root, "apiKey");
        Optional<String> apiKeyEnv = readOptionalString(root, "apiKeyEnv");
        Optional<Double> temperature = readOptionalDouble(root, "temperature");
        Optional<Double> topP = readOptionalDouble(root, "topP");
        Optional<Integer> maxTokens = readOptionalInt(root, "maxTokens");
        Duration timeout = Duration.ofSeconds(readInt(root, "timeoutSeconds", (int) defaults.timeout().toSeconds()));
        int maxRetries = readInt(root, "maxRetries", defaults.maxRetries());
        long cooldownMillis = readLong(root, "cooldownMillis", defaults.cooldownMillis());
        int maxToolCalls = readInt(root, "maxToolCalls", defaults.maxToolCalls());
        int maxIterations = readInt(root, "maxIterations", defaults.maxIterations());
        boolean strictJsonSchema = readBoolean(root, "strictJsonSchema", defaults.strictJsonSchema());
        boolean logRequests = readBoolean(root, "logRequests", defaults.logRequests());
        boolean logResponses = readBoolean(root, "logResponses", defaults.logResponses());

        return new LlmConfig(provider, model, baseUrl, apiKey, apiKeyEnv, temperature, topP, maxTokens,
                timeout, maxRetries, cooldownMillis, maxToolCalls, maxIterations,
                strictJsonSchema, logRequests, logResponses);
    }

    private static LlmProvider parseProvider(String raw, LlmProvider fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LlmProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String readString(Config root, String key, String fallback) {
        Object value = root.get(key);
        if (!(value instanceof String stringValue)) {
            return fallback;
        }
        return stringValue.isBlank() ? fallback : stringValue;
    }

    private static Optional<String> readOptionalString(Config root, String key) {
        Object value = root.get(key);
        if (!(value instanceof String stringValue)) {
            return Optional.empty();
        }
        return stringValue.isBlank() ? Optional.empty() : Optional.of(stringValue);
    }

    private static Optional<Double> readOptionalDouble(Config root, String key) {
        Object value = root.get(key);
        if (!(value instanceof Number numberValue)) {
            return Optional.empty();
        }
        return Optional.of(numberValue.doubleValue());
    }

    private static Optional<Integer> readOptionalInt(Config root, String key) {
        Object value = root.get(key);
        if (!(value instanceof Number numberValue)) {
            return Optional.empty();
        }
        return Optional.of(numberValue.intValue());
    }

    private static int readInt(Config root, String key, int fallback) {
        Object value = root.get(key);
        if (!(value instanceof Number numberValue)) {
            return fallback;
        }
        return numberValue.intValue();
    }

    private static long readLong(Config root, String key, long fallback) {
        Object value = root.get(key);
        if (!(value instanceof Number numberValue)) {
            return fallback;
        }
        return numberValue.longValue();
    }

    private static boolean readBoolean(Config root, String key, boolean fallback) {
        Object value = root.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            return fallback;
        }
        return booleanValue;
    }
}
