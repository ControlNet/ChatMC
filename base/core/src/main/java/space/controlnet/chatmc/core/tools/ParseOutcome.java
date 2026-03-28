package space.controlnet.chatmc.core.tools;

public record ParseOutcome(ToolCall call, String errorCode, String errorMessage) {
}
