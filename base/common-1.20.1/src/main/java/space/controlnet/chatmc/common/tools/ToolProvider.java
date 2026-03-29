package space.controlnet.chatmc.common.tools;

import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;

import java.util.List;
import java.util.Optional;

/**
 * Tool provider for registering tool specs and executing tool calls.
 */
public interface ToolProvider {
    enum ExecutionAffinity {
        SERVER_THREAD,
        CALLING_THREAD
    }

    List<AgentTool> specs();

    default ExecutionAffinity executionAffinity() {
        return ExecutionAffinity.SERVER_THREAD;
    }

    ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved);
}
