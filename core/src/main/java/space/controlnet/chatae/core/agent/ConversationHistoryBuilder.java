package space.controlnet.chatae.core.agent;

import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;

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
     * @param messages    The list of chat messages from the session
     * @param maxMessages Maximum number of recent messages to include
     * @return Formatted conversation history string
     */
    public static String build(List<ChatMessage> messages, int maxMessages) {
        if (messages == null || messages.isEmpty()) {
            return "(No conversation history)";
        }

        int startIndex = Math.max(0, messages.size() - maxMessages);
        List<ChatMessage> recentMessages = messages.subList(startIndex, messages.size());

        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : recentMessages) {
            String prefix = formatRolePrefix(message.role());
            String text = message.text();
            if (text == null) {
                text = "";
            }
            // Truncate very long messages
            if (text.length() > 500) {
                text = text.substring(0, 500) + "... (truncated)";
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
