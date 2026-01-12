package space.controlnet.chatae.core.tools;

public record ParseOutcome(ToolCall call, String errorCode, String errorMessage) {
}
