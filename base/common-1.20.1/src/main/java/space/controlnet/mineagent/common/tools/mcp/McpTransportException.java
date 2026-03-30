package space.controlnet.mineagent.common.tools.mcp;

public final class McpTransportException extends Exception {
    private final String failureCode;
    private final String failureMessage;

    private McpTransportException(String failureCode, String failureMessage, Throwable cause) {
        super(failureMessage, cause);
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public static McpTransportException timeout(Throwable cause) {
        return new McpTransportException("tool_timeout", "tool execution timeout", cause);
    }

    public static McpTransportException executionFailed(Throwable cause) {
        return new McpTransportException("tool_execution_failed", "tool execution failed", cause);
    }

    public String failureCode() {
        return failureCode;
    }

    public String failureMessage() {
        return failureMessage;
    }
}
