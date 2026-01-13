package space.controlnet.chatae.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.core.agent.PromptContext;
import space.controlnet.chatae.core.agent.PromptId;
import space.controlnet.chatae.core.agent.PromptTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class PromptRuntime {
    private static final AtomicReference<PromptStore> STORE = new AtomicReference<>(new PromptStore());

    private PromptRuntime() {
    }

    public static void reload(MinecraftServer server) {
        STORE.set(PromptFileManager.loadAll(server));
    }

    public static String render(PromptId promptId, String effectiveLocale, java.util.Map<String, String> variables) {
        PromptStore store = STORE.get();
        String template = store.resolve(new PromptContext(promptId, effectiveLocale, variables));
        return PromptTemplate.render(template, variables);
    }

    public static Optional<String> promptHash(String prompt) {
        if (prompt == null) {
            return Optional.empty();
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return Optional.of(sb.toString());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
