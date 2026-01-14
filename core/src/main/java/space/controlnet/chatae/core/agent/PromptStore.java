package space.controlnet.chatae.core.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for prompt templates, keyed by prompt ID and locale.
 */
public final class PromptStore {
    private final ConcurrentHashMap<PromptKey, String> prompts = new ConcurrentHashMap<>();

    public void clear() {
        prompts.clear();
    }

    public void put(PromptId id, String locale, String content) {
        prompts.put(new PromptKey(id.id(), locale), content);
    }

    /**
     * Resolves a prompt template for the given context.
     * Falls back to en_us if the requested locale is not found.
     *
     * @param context the prompt context containing ID, locale, and variables
     * @return the prompt template, or empty string if not found
     */
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

    /**
     * Key for prompt lookup combining prompt ID and locale.
     */
    public record PromptKey(String promptId, String locale) {
    }
}
