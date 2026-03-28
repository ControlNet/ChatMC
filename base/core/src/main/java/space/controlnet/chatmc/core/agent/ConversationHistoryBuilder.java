package space.controlnet.chatmc.core.agent;

import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;

import java.util.List;

/**
 * Builds a formatted conversation history string from session messages
 * for inclusion in the agent reasoning prompt.
 */
public final class ConversationHistoryBuilder {

    private ConversationHistoryBuilder() {
    }

    /**
     * Build a formatted conversation history string from session messages.
     *
     * @param messages The list of chat messages from the session
     * @return Formatted conversation history string
     */
    public static String build(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(No conversation history)";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String prefix = formatRolePrefix(message.role());
            String text = message.text();
            if (text == null) {
                text = "";
            }
            sb.append(prefix).append(": ").append(text).append("\n");
        }

        return sb.toString().trim();
    }

    private static String formatRolePrefix(ChatRole role) {
        return switch (role) {
            case USER -> "User";
            case ASSISTANT -> "Assistant";
            case TOOL -> "Tool Result";
            case SYSTEM -> "System";
        };
    }
}
