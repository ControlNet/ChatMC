package space.controlnet.mineagent.core.agent;

import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

/**
 * Context interface for agent session operations.
 * This abstracts away MC-specific session access and tool execution.
 */
public interface AgentSessionContext extends Serializable {
    /**
     * Gets the current session snapshot.
     */
    Optional<SessionSnapshot> getSession(UUID sessionId);

    /**
     * Appends a message to the session.
     */
    void appendMessage(UUID sessionId, ChatMessage message);

    /**
     * Gets the terminal context for tool execution.
     */
    Optional<TerminalContext> getTerminal(AgentPlayerContext player);

    /**
     * Executes a tool call.
     */
    ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved);

    /**
     * Gets the available tool specifications.
     */
    java.util.List<space.controlnet.mineagent.core.tools.AgentTool> getToolSpecs();

    /**
     * Renders a prompt template.
     */
    String renderPrompt(PromptId promptId, String locale, java.util.Map<String, String> variables);

    /**
     * Logs a debug message.
     */
    void logDebug(String message, Object... args);

    /**
     * Logs an error message.
     */
    void logError(String message, Throwable error);
}
