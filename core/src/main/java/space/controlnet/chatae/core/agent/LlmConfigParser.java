package space.controlnet.chatae.core.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.util.Optional;

/**
 * Parser for LLM configuration JSON.
 * This class handles pure JSON parsing without file I/O.
 */
public final class LlmConfigParser {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LlmConfigParser() {
    }

    /**
     * Parses LLM configuration from a JSON string.
     *
     * @param json     the JSON string to parse
     * @param defaults default values to use for missing fields
     * @return the parsed configuration, or defaults if parsing fails
     */
    public static LlmConfig parse(String json, LlmConfig defaults) {
        if (json == null || json.isBlank()) {
            return defaults;
        }
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            return parseFromJson(root, defaults);
        } catch (Exception e) {
            return defaults;
        }
    }

    /**
     * Parses LLM configuration from a JsonObject.
     *
     * @param root     the JSON object to parse
     * @param defaults default values to use for missing fields
     * @return the parsed configuration
     */
    public static LlmConfig parseFromJson(JsonObject root, LlmConfig defaults) {
        if (root == null) {
            return defaults;
        }

        LlmProvider provider = parseProvider(root, defaults.provider());
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
        boolean strictJsonSchema = readBoolean(root, "strictJsonSchema", defaults.strictJsonSchema());
        boolean logRequests = readBoolean(root, "logRequests", defaults.logRequests());
        boolean logResponses = readBoolean(root, "logResponses", defaults.logResponses());

        return new LlmConfig(provider, model, baseUrl, apiKey, apiKeyEnv, temperature, topP, maxTokens,
                timeout, maxRetries, cooldownMillis, strictJsonSchema, logRequests, logResponses);
    }

    /**
     * Serializes LLM configuration to a JSON string.
     *
     * @param config the configuration to serialize
     * @return the JSON string representation
     */
    public static String toJson(LlmConfig config) {
        JsonObject root = toJsonObject(config);
        return GSON.toJson(root);
    }

    /**
     * Converts LLM configuration to a JsonObject.
     *
     * @param config the configuration to convert
     * @return the JSON object representation
     */
    public static JsonObject toJsonObject(LlmConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("provider", config.provider().name());
        root.addProperty("model", config.model());
        config.baseUrl().ifPresent(value -> root.addProperty("baseUrl", value));
        config.apiKey().ifPresent(value -> root.addProperty("apiKey", value));
        config.apiKeyEnv().ifPresent(value -> root.addProperty("apiKeyEnv", value));
        config.temperature().ifPresent(value -> root.addProperty("temperature", value));
        config.topP().ifPresent(value -> root.addProperty("topP", value));
        config.maxTokens().ifPresent(value -> root.addProperty("maxTokens", value));
        root.addProperty("timeoutSeconds", config.timeout().toSeconds());
        root.addProperty("maxRetries", config.maxRetries());
        root.addProperty("cooldownMillis", config.cooldownMillis());
        root.addProperty("strictJsonSchema", config.strictJsonSchema());
        root.addProperty("logRequests", config.logRequests());
        root.addProperty("logResponses", config.logResponses());
        return root;
    }

    private static LlmProvider parseProvider(JsonObject root, LlmProvider fallback) {
        String raw = readString(root, "provider", fallback.name());
        try {
            return LlmProvider.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Optional<String> readOptionalString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Double> readOptionalDouble(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsDouble());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> readOptionalInt(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsInt());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long readLong(JsonObject root, String key, long fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }
}
