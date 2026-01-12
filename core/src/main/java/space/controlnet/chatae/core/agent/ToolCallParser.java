package space.controlnet.chatae.core.agent;

import space.controlnet.chatae.core.tools.ToolCall;

import java.util.Optional;

/**
 * Platform-neutral tool call parsing interface.
 * Implementations can use LLM APIs or simple pattern matching.
 */
public interface ToolCallParser {
    /**
     * Parses a user message into a tool call.
     * @param message The user's message
     * @return Optional tool call if parsing succeeds
     */
    Optional<ToolCall> parse(String message);
}
