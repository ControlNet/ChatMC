package space.controlnet.chatae.common.llm;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.agent.LlmConfig;
import space.controlnet.chatae.core.agent.LlmConfigParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads LLM configuration from the config directory.
 * Delegates JSON parsing to {@link LlmConfigParser}.
 */
public final class LlmConfigLoader {
    private static final String CONFIG_DIR = "chatae";
    private static final String CONFIG_FILE = "llm.json";

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
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return LlmConfigParser.parse(json, defaults);
        } catch (Exception e) {
            ChatAE.LOGGER.warn("Failed to read LLM config, using defaults", e);
            return defaults;
        }
    }

    private static void writeDefaults(Path path, LlmConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            String json = LlmConfigParser.toJson(defaults);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to write default LLM config", e);
        }
    }
}
