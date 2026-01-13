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
                ensureDefaultPrompt(defaultPath, id, locale, server);
                String content = readFirstExisting(configDir, id, locale, defaultPath, server);
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

    private static void ensureDefaultPrompt(Path path, PromptId id, String locale, MinecraftServer server) {
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            String fallback = loadDefaultPrompt(server, id, locale);
            Files.writeString(path, fallback == null ? "" : fallback, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatAE.LOGGER.warn("Failed to write prompt defaults", e);
        }
    }

    private static String readFirstExisting(Path configDir, PromptId id, String locale, Path defaultPath, MinecraftServer server) {
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
        return loadDefaultPrompt(server, id, "en_us");
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

    private static String loadDefaultPrompt(MinecraftServer server, PromptId id, String locale) {
        String key = "prompt.chatae." + id.id();
        String resource = resolveLangEntry(server, locale, key);
        return resource == null ? resolveLangEntry(server, "en_us", key) : resource;
    }

    private static String resolveLangEntry(MinecraftServer server, String locale, String key) {
        if (server == null) {
            return null;
        }
        try {
            net.minecraft.server.packs.resources.ResourceManager manager = server.getResourceManager();
            net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation("chatae", "lang/" + locale + ".json");
            java.util.Optional<net.minecraft.server.packs.resources.Resource> resource = manager.getResource(loc);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                java.util.Map<String, String> map = new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), java.util.Map.class);
                if (map == null) {
                    return null;
                }
                return map.get(key);
            }
        } catch (Exception e) {
            ChatAE.LOGGER.warn("Failed to read lang entry {}:{}", locale, key, e);
            return null;
        }
    }
}
