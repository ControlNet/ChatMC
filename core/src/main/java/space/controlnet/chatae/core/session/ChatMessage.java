package space.controlnet.chatae.core.session;

public record ChatMessage(ChatRole role, String text, long timestampMillis) {
    public static ChatMessage user(String text, long timestampMillis) {
        return new ChatMessage(ChatRole.USER, text, timestampMillis);
    }

    public static ChatMessage assistant(String text, long timestampMillis) {
        return new ChatMessage(ChatRole.ASSISTANT, text, timestampMillis);
    }
}
