package space.controlnet.chatmc.core.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves and renders prompt templates with variable substitution.
 */
public final class PromptResolver {
    private final PromptStore store;

    public PromptResolver(PromptStore store) {
        this.store = store;
    }

    /**
     * Resolves and renders a prompt template with the given variables.
     *
     * @param promptId        the prompt identifier
     * @param effectiveLocale the locale to use for resolution
     * @param variables       variables to substitute in the template
     * @return the rendered prompt string
     */
    public String resolve(PromptId promptId, String effectiveLocale, Map<String, String> variables) {
        String template = store.resolve(new PromptContext(promptId, effectiveLocale, variables));
        return PromptTemplate.render(template, variables);
    }

    /**
     * Computes a SHA-256 hash of the given prompt string.
     *
     * @param prompt the prompt to hash
     * @return the hex-encoded hash, or empty if hashing fails
     */
    public static Optional<String> computeHash(String prompt) {
        if (prompt == null) {
            return Optional.empty();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
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
