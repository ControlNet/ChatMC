package space.controlnet.chatmc.common.llm;

import com.google.gson.Gson;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.core.agent.PromptId;
import space.controlnet.chatmc.core.agent.PromptStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static space.controlnet.chatmc.common.ChatMC.MOD_ID;

public final class PromptFileManager {
    private static final String PROMPT_DIR = "chatmc/prompts";

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
            ChatMC.LOGGER.warn("Failed to enumerate language resources", e);
        }
        if (locales.isEmpty()) {
            locales.addAll(discoverLocalesFromClasspath());
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
            ChatMC.LOGGER.warn("Failed to write prompt defaults", e);
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
            ChatMC.LOGGER.warn("Failed to read prompt file {}", path, e);
            return null;
        }
    }

    private static String loadDefaultPrompt(MinecraftServer server, PromptId id, String locale) {
        String key = "prompt.chatmc." + id.id();
        String resource = resolveLangEntry(server, locale, key);
        return resource == null ? resolveLangEntry(server, "en_us", key) : resource;
    }

    @SuppressWarnings("unchecked")
    private static String resolveLangEntry(MinecraftServer server, String locale, String key) {
        Map<String, String> map = readLangMapFromServer(server, locale);
        if (map == null) {
            map = readLangMapFromClasspath(locale);
        }
        if (map == null) {
            return null;
        }
        return map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readLangMapFromServer(MinecraftServer server, String locale) {
        if (server == null) {
            return null;
        }
        try {
            net.minecraft.server.packs.resources.ResourceManager manager = server.getResourceManager();
            net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation("chatmc", "lang/" + locale + ".json");
            Optional<Resource> resource = manager.getResource(loc);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
            }
        } catch (Exception e) {
            ChatMC.LOGGER.warn("Failed to read lang entry {} from server resources", locale, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readLangMapFromClasspath(String locale) {
        String path = "assets/chatmc/lang/" + locale + ".json";
        try (InputStream in = PromptFileManager.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            ChatMC.LOGGER.warn("Failed to read lang entry {} from classpath", locale, e);
            return null;
        }
    }

    private static Set<String> discoverLocalesFromClasspath() {
        Set<String> locales = new HashSet<>();
        Enumeration<URL> resources;
        try {
            resources = PromptFileManager.class.getClassLoader().getResources("assets/" + MOD_ID + "/lang");
        } catch (IOException e) {
            ChatMC.LOGGER.warn("Failed to enumerate classpath language resources", e);
            return locales;
        }

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                try {
                    File dir = new File(url.toURI());
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        String name = file.getName();
                        locales.add(name.substring(0, name.length() - ".json".length()));
                    }
                } catch (URISyntaxException e) {
                    ChatMC.LOGGER.warn("Failed to read lang directory {}", url, e);
                }
            } else if ("jar".equals(protocol)) {
                try {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith("assets/chatmc/lang/") && name.endsWith(".json")) {
                                String locale = name.substring("assets/chatmc/lang/".length(), name.length() - ".json".length());
                                locales.add(locale);
                            }
                        }
                    }
                } catch (IOException e) {
                    ChatMC.LOGGER.warn("Failed to read lang resources from jar {}", url, e);
                }
            } else {
                try {
                    Path dir = Path.of(url.toURI());
                    if (!Files.isDirectory(dir)) {
                        continue;
                    }
                    try (var stream = Files.list(dir)) {
                        stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                                .forEach(path -> {
                                    String name = path.getFileName().toString();
                                    locales.add(name.substring(0, name.length() - ".json".length()));
                                });
                    }
                } catch (Exception e) {
                    ChatMC.LOGGER.warn("Failed to read lang resources from {}", url, e);
                }
            }
        }

        return locales;
    }
}
