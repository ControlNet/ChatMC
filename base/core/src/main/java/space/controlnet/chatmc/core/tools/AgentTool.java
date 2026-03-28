package space.controlnet.chatmc.core.tools;

import java.util.List;

/**
 * Describes a tool for prompt rendering and UI formatting.
 */
public interface AgentTool {
    String name();

    String description();

    String argsSchema();

    List<String> argsDescription();

    String resultSchema();

    List<String> resultDescription();

    List<String> examples();

    ToolRender render(ToolPayload payload);
}
