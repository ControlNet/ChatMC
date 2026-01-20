package space.controlnet.chatmc.common.llm;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.core.agent.LlmConfig;
import space.controlnet.chatmc.core.agent.LlmConfigParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads LLM configuration from the config directory.
 */
public final class LlmConfigLoader {
    private static final String CONFIG_DIR = "chatmc";
    private static final String CONFIG_FILE = "llm.toml";

    private LlmConfigLoader() {
    }

    public static LlmConfig load(MinecraftServer server) {
        Path path = Platform.getConfigFolder().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
        LlmConfig defaults = LlmConfig.defaults();

        if (!Files.exists(path)) {
            writeDefaults(path, defaults);
            return defaults;
        }

        try (var reader = Files.newBufferedReader(path)) {
            return LlmConfigParser.parse(reader, defaults);
        } catch (Exception e) {
            ChatMC.LOGGER.warn("Failed to read LLM config, using defaults", e);
            return defaults;
        }
    }

    private static void writeDefaults(Path path, LlmConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                LlmConfigParser.writeToml(writer, defaults);
            }
        } catch (IOException e) {
            ChatMC.LOGGER.warn("Failed to write default LLM config", e);
        }
    }
}
