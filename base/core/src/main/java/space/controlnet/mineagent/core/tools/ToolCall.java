package space.controlnet.mineagent.core.tools;

import java.io.Serializable;

public record ToolCall(String toolName, String argsJson) implements Serializable {
}
