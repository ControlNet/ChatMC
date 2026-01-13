package space.controlnet.chatae.common.llm;

import space.controlnet.chatae.core.agent.PromptContext;
import space.controlnet.chatae.core.agent.PromptId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptStore {
    private final ConcurrentHashMap<PromptKey, String> prompts = new ConcurrentHashMap<>();

    public void clear() {
        prompts.clear();
    }

    public void put(PromptId id, String locale, String content) {
        prompts.put(new PromptKey(id.id(), locale), content);
    }

    public String resolve(PromptContext context) {
        String locale = context.effectiveLocale();
        String exact = prompts.get(new PromptKey(context.promptId().id(), locale));
        if (exact != null) {
            return exact;
        }
        String fallback = prompts.get(new PromptKey(context.promptId().id(), "en_us"));
        return fallback == null ? "" : fallback;
    }

    public void loadAll(Map<PromptKey, String> values) {
        prompts.clear();
        prompts.putAll(values);
    }

    public record PromptKey(String promptId, String locale) {
    }
}
