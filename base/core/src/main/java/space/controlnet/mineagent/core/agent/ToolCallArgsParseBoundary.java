package space.controlnet.mineagent.core.agent;

final class ToolCallArgsParseBoundary {
    static final int MAX_ARGS_JSON_LENGTH = 65_536;
    static final String BOUNDARY_SIGNAL = "PARSE_BOUNDARY_TOOL_ARGS_TOO_LARGE";

    private ToolCallArgsParseBoundary() {
    }

    static void validate(String tool, String argsJson) {
        if (argsJson == null) {
            return;
        }
        int length = argsJson.length();
        if (length > MAX_ARGS_JSON_LENGTH) {
            throw new ToolCallParseBoundaryException(
                    BOUNDARY_SIGNAL
                            + ": tool='" + tool + "', argsJson.length=" + length
                            + ", max=" + MAX_ARGS_JSON_LENGTH
            );
        }
    }

    static final class ToolCallParseBoundaryException extends RuntimeException {
        ToolCallParseBoundaryException(String message) {
            super(message);
        }
    }
}
