package space.controlnet.chatmc.core.tools;

public record ToolResult(boolean success, String payloadJson, ToolError error) {
    public static ToolResult ok(String payloadJson) {
        return new ToolResult(true, payloadJson, null);
    }

    public static ToolResult error(String code, String message) {
        return new ToolResult(false, null, new ToolError(code, message));
    }

    public static ToolResult error(String payloadJson, String code, String message) {
        return new ToolResult(false, payloadJson, new ToolError(code, message));
    }
}
