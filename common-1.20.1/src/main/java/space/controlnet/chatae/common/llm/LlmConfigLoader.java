package space.controlnet.chatae.common.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.agent.LlmConfig;
import space.controlnet.chatae.core.agent.LlmProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public final class LlmConfigLoader {
    private static final String CONFIG_DIR = "chatae";
    private static final String CONFIG_FILE = "llm.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LlmConfigLoader() {
    }

    public static LlmConfig load(MinecraftServer server) {
        Path path = Platform.getConfigFolder().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
        LlmConfig defaults = LlmConfig.defaults();
        if (!Files.exists(path)) {
            writeDefaults(path, defaults);
            return defaults;
        }

        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
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
            boolean strictJsonSchema = readBoolean(root, "strictJsonSchema", defaults.strictJsonSchema());
            boolean logRequests = readBoolean(root, "logRequests", defaults.logRequests());
            boolean logResponses = readBoolean(root, "logResponses", defaults.logResponses());

            return new LlmConfig(provider, model, baseUrl, apiKey, apiKeyEnv, temperature, topP, maxTokens, timeout, maxRetries, strictJsonSchema, logRequests, logResponses);
        } catch (Exception e) {
            ChatAE.LOGGER.warn("Failed to read LLM config, using defaults", e);
            return defaults;
        }
    }

    private static void writeDefaults(Path path, LlmConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("provider", defaults.provider().name());
            root.addProperty("model", defaults.model());
            defaults.baseUrl().ifPresent(value -> root.addProperty("baseUrl", value));
            defaults.apiKey().ifPresent(value -> root.addProperty("apiKey", value));
            defaults.apiKeyEnv().ifPresent(value -> root.addProperty("apiKeyEnv", value));
            defaults.temperature().ifPresent(value -> root.addProperty("temperature", value));
            defaults.topP().ifPresent(value -> root.addProperty("topP", value));
            defaults.maxTokens().ifPresent(value -> root.addProperty("maxTokens", value));
            root.addProperty("timeoutSeconds", defaults.timeout().toSeconds());
            root.addProperty("maxRetries", defaults.maxRetries());
            root.addProperty("strictJsonSchema", defaults.strictJsonSchema());
            root.addProperty("logRequests", defaults.logRequests());
            root.addProperty("logResponses", defaults.logResponses());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to write default LLM config", e);
        }
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
