package space.controlnet.mineagent.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.mineagent.core.agent.PromptContext;
import space.controlnet.mineagent.core.agent.PromptId;
import space.controlnet.mineagent.core.agent.PromptResolver;
import space.controlnet.mineagent.core.agent.PromptStore;
import space.controlnet.mineagent.core.agent.PromptTemplate;

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
        return PromptResolver.computeHash(prompt);
    }
}
