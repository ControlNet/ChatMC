package space.controlnet.chatmc.common.tools.mcp;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.core.tools.mcp.McpConfig;
import space.controlnet.chatmc.core.tools.mcp.McpConfigParser;
import space.controlnet.chatmc.core.tools.mcp.McpConfigValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class McpConfigLoader {
    private static final String CONFIG_DIR = "chatmc";
    private static final String CONFIG_FILE = "mcp.json";

    private McpConfigLoader() {
    }

    public static McpConfig load(MinecraftServer server) {
        return loadResult(Platform.getConfigFolder()).config();
    }

    static McpConfig load(Path configRoot) {
        return loadResult(configRoot).config();
    }

    static LoadResult loadResult(Path configRoot) {
        Path path = resolveConfigPath(configRoot);
        McpConfig defaults = McpConfig.defaults();

        if (!Files.exists(path)) {
            writeDefaults(path, defaults);
            return new LoadResult(defaults, path, false);
        }

        try (var reader = Files.newBufferedReader(path)) {
            McpConfig parsed = McpConfigParser.parse(reader);
            List<String> errors = McpConfigValidator.validate(parsed);
            if (!errors.isEmpty()) {
                ChatMC.LOGGER.warn("Invalid MCP config at {}: {}", path, String.join("; ", errors));
                return new LoadResult(defaults, path, false);
            }
            return new LoadResult(parsed, path, true);
        } catch (Exception exception) {
            ChatMC.LOGGER.warn("Failed to read MCP config, using defaults", exception);
            return new LoadResult(defaults, path, false);
        }
    }

    static Path resolveConfigPath(Path configRoot) {
        return configRoot.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    private static void writeDefaults(Path path, McpConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                McpConfigParser.writeJson(writer, defaults);
            }
        } catch (IOException exception) {
            ChatMC.LOGGER.warn("Failed to write default MCP config", exception);
        }
    }

    record LoadResult(McpConfig config, Path path, boolean loadedFromDisk) {
        LoadResult {
            config = config == null ? McpConfig.defaults() : config;
            path = path == null ? Path.of(CONFIG_DIR, CONFIG_FILE) : path;
        }
    }
}
