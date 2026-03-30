package space.controlnet.mineagent.core.tools;

public record ParseOutcome(ToolCall call, String errorCode, String errorMessage) {
}
