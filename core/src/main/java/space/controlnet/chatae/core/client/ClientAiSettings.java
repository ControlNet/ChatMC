package space.controlnet.chatae.core.client;

public final class ClientAiSettings {
    private static String aiLocaleOverride = "";

    private ClientAiSettings() {
    }

    public static String getAiLocaleOverride() {
        return aiLocaleOverride;
    }

    public static void setAiLocaleOverride(String value) {
        aiLocaleOverride = value == null ? "" : value.trim();
    }
}
