package space.controlnet.chatae.core.tools;

public record ToolResult(boolean success, String payloadJson, ToolError error) {
    public static ToolResult ok(String payloadJson) {
        return new ToolResult(true, payloadJson, null);
    }

    public static ToolResult error(String code, String message) {
        return new ToolResult(false, null, new ToolError(code, message));
    }
}
