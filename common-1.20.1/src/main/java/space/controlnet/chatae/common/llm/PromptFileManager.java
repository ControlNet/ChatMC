package space.controlnet.chatae.common.llm;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.agent.PromptId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PromptFileManager {
    private static final String PROMPT_DIR = "chatae/prompts";

    private PromptFileManager() {
    }

    public static PromptStore loadAll(MinecraftServer server) {
        PromptStore store = new PromptStore();
        Path configDir = Platform.getConfigFolder().resolve(PROMPT_DIR);
        Map<PromptStore.PromptKey, String> prompts = new HashMap<>();

        Set<String> locales = discoverLocales(server);
        for (String locale : locales) {
            for (PromptId id : PromptId.values()) {
                Path defaultPath = configDir.resolve(id.id() + "." + locale + ".default.prompt");
                ensureDefaultPrompt(defaultPath, id, locale);
                String content = readFirstExisting(configDir, id, locale, defaultPath);
                if (content != null) {
                    prompts.put(new PromptStore.PromptKey(id.id(), locale), content);
                }
            }
        }

        store.loadAll(prompts);
        return store;
    }

    private static Set<String> discoverLocales(MinecraftServer server) {
        Set<String> locales = new HashSet<>();
        try {
            if (server != null) {
                server.getResourceManager().listResources("lang", path -> path.getPath().endsWith(".json"))
                        .forEach((id, res) -> {
                            String path = id.getPath();
                            if (path.startsWith("lang/") && path.endsWith(".json")) {
                                String locale = path.substring("lang/".length(), path.length() - ".json".length());
                                locales.add(locale);
                            }
                        });
            }
        } catch (Exception e) {
            ChatAE.LOGGER.warn("Failed to enumerate language resources", e);
        }
        if (locales.isEmpty()) {
            locales.add("en_us");
        }
        return locales;
    }

    private static void ensureDefaultPrompt(Path path, PromptId id, String locale) {
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            String resourcePath = "/assets/chatae/prompts/" + id.id() + "." + locale + ".default.prompt";
            try (InputStream in = PromptFileManager.class.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
            String fallback = loadDefaultPrompt(id, "en_us");
            Files.writeString(path, fallback == null ? "" : fallback, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to write prompt defaults", e);
        }
    }

    private static String readFirstExisting(Path configDir, PromptId id, String locale, Path defaultPath) {
        Path override = configDir.resolve(id.id() + ".prompt");
        Path localeOverride = configDir.resolve(id.id() + "." + locale + ".prompt");

        String content = readFileIfExists(override);
        if (content != null) {
            return content;
        }
        content = readFileIfExists(localeOverride);
        if (content != null) {
            return content;
        }
        content = readFileIfExists(defaultPath);
        if (content != null) {
            return content;
        }
        return loadDefaultPrompt(id, "en_us");
    }

    private static String readFileIfExists(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to read prompt file {}", path, e);
            return null;
        }
    }

    private static String loadDefaultPrompt(PromptId id, String locale) {
        String resourcePath = "/assets/chatae/prompts/" + id.id() + "." + locale + ".default.prompt";
        try (InputStream in = PromptFileManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to read prompt resource {}", resourcePath, e);
            return null;
        }
    }
}
