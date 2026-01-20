package space.controlnet.chatmc.core.agent;

import space.controlnet.chatmc.core.tools.ToolCall;

import java.util.Optional;

/**
 * Platform-neutral tool call parsing interface.
 * Implementations can use LLM APIs or simple pattern matching.
 */
public interface ToolCallParser {
    Optional<ToolCall> parse(String prompt);
}
