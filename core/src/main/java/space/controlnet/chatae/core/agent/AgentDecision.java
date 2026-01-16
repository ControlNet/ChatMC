package space.controlnet.chatae.core.agent;

import space.controlnet.chatae.core.tools.ToolCall;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Represents the LLM's decision at each step of the agent loop.
 * The agent can either call a tool or respond directly to the user.
 */
public record AgentDecision(
        AgentAction action,
        Optional<String> thinking,
        List<ToolCall> toolCalls,
        Optional<String> response
) implements Serializable {
    public AgentDecision {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public enum AgentAction {
        TOOL_CALL,
        RESPOND
    }

    /**
     * Create a decision to call a tool.
     */
    public static AgentDecision toolCall(ToolCall call, String thinking) {
        return toolCalls(List.of(call), thinking);
    }

    /**
     * Create a decision to call multiple tools.
     */
    public static AgentDecision toolCalls(List<ToolCall> calls, String thinking) {
        return new AgentDecision(
                AgentAction.TOOL_CALL,
                Optional.ofNullable(thinking),
                calls,
                Optional.empty());
    }

    /**
     * Create a decision to respond directly to the user.
     */
    public static AgentDecision respond(String response, String thinking) {
        return new AgentDecision(
                AgentAction.RESPOND,
                Optional.ofNullable(thinking),
                List.of(),
                Optional.of(response)
        );
    }

    /**
     * Check if this decision is to call a tool.
     */
    public boolean isToolCall() {
        return action == AgentAction.TOOL_CALL;
    }

    /**
     * Check if this decision is to respond to the user.
     */
    public boolean isRespond() {
        return action == AgentAction.RESPOND;
    }
}
