package space.controlnet.chatae.core.tools;

import java.io.Serializable;

public record ToolCall(String toolName, String argsJson) implements Serializable {
}
