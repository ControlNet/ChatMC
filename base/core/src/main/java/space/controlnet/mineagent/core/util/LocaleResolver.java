package space.controlnet.mineagent.core.util;

/**
 * Utility for resolving effective locale from client and override settings.
 */
public final class LocaleResolver {
    private LocaleResolver() {
    }

    /**
     * Resolves the effective locale to use for AI responses.
     *
     * @param clientLocale     the client's locale (e.g., from Minecraft language settings)
     * @param aiLocaleOverride optional override specified by the user
     * @return the effective locale to use, defaults to "en_us" if both are blank
     */
    public static String resolveEffectiveLocale(String clientLocale, String aiLocaleOverride) {
        String override = aiLocaleOverride == null ? "" : aiLocaleOverride.trim();
        if (!override.isBlank()) {
            return override;
        }
        String locale = clientLocale == null ? "" : clientLocale.trim();
        return locale.isBlank() ? "en_us" : locale;
    }
}
