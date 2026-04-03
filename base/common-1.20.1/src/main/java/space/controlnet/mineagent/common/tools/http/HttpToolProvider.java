package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;
import space.controlnet.mineagent.core.util.JsonSupport;

import java.util.List;
import java.util.Optional;

public final class HttpToolProvider implements ToolProvider {
    private static final List<AgentTool> TOOLS = List.of(HttpToolMetadata.spec());
    private static final String INVALID_ARGUMENTS_MESSAGE = "HTTP tool arguments are invalid.";

    @Override
    public List<AgentTool> specs() {
        return TOOLS;
    }

    @Override
    public ExecutionAffinity executionAffinity() {
        return ExecutionAffinity.CALLING_THREAD;
    }

    @Override
    public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        if (call == null || call.toolName() == null || call.toolName().isBlank()) {
            return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
        }
        if (!HttpToolRequest.TOOL_NAME.equals(call.toolName())) {
            return ToolOutcome.result(ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName()));
        }

        try {
            return ToolOutcome.result(HttpToolExecution.execute(parseRequest(call.argsJson())));
        } catch (HttpToolRequestPreparation.ValidationException validationException) {
            return ToolOutcome.result(errorWithPayload(validationException.request(), "invalid_args",
                    requireMessage(validationException, INVALID_ARGUMENTS_MESSAGE)));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ToolOutcome.result(ToolResult.error("invalid_args", requireMessage(illegalArgumentException,
                    INVALID_ARGUMENTS_MESSAGE)));
        }
    }

    private static HttpToolRequestPreparation parseRequest(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            throw new IllegalArgumentException("HTTP tool arguments must be a JSON object.");
        }

        JsonObject parsedArguments;
        try {
            JsonElement parsed = JsonParser.parseString(argsJson);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("HTTP tool arguments must be a JSON object.");
            }
            parsedArguments = parsed.getAsJsonObject();
        } catch (RuntimeException runtimeException) {
            if (runtimeException instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("HTTP tool arguments must be valid JSON.", runtimeException);
        }

        return HttpToolRequestPreparation.prepare(parsedArguments);
    }

    private static ToolResult errorWithPayload(HttpToolRequest request, String code, String message) {
        String payloadJson = JsonSupport.GSON.toJson(new HttpToolResultEnvelope(request,
                new HttpToolFailure(code, message), false));
        return ToolResult.error(payloadJson, code, message);
    }

    private static String requireMessage(IllegalArgumentException exception, String fallbackMessage) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallbackMessage : message;
    }
}
