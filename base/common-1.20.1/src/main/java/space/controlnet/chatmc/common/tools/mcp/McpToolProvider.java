package space.controlnet.chatmc.common.tools.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.mcp.McpSchemaMapper.McpProjectedTool;
import space.controlnet.chatmc.core.util.JsonSupport;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class McpToolProvider implements ToolProvider, AutoCloseable {
    private static final String DEFAULT_REMOTE_ERROR_CODE = "tool_execution_failed";

    private final String serverAlias;
    private final McpClientSession session;
    private final List<McpProjectedTool> projectedTools;
    private final Map<String, McpProjectedTool> projectedToolsByQualifiedName;
    private final List<AgentTool> toolSpecs;

    McpToolProvider(String serverAlias, McpClientSession session, List<McpProjectedTool> projectedTools) {
        this.serverAlias = requireText(serverAlias, "serverAlias");
        this.session = Objects.requireNonNull(session, "session");
        this.projectedTools = projectedTools == null ? List.of() : List.copyOf(projectedTools);

        LinkedHashMap<String, McpProjectedTool> toolsByName = new LinkedHashMap<>();
        for (McpProjectedTool projectedTool : this.projectedTools) {
            if (projectedTool == null) {
                continue;
            }
            toolsByName.put(projectedTool.qualifiedToolName(), projectedTool);
        }
        this.projectedToolsByQualifiedName = Map.copyOf(toolsByName);
        this.toolSpecs = this.projectedTools.stream()
                .filter(Objects::nonNull)
                .map(McpProjectedTool::toolSpec)
                .toList();
    }

    String serverAlias() {
        return serverAlias;
    }

    McpClientSession session() {
        return session;
    }

    List<McpProjectedTool> projectedTools() {
        return projectedTools;
    }

    Optional<McpProjectedTool> projectedTool(String qualifiedToolName) {
        if (qualifiedToolName == null || qualifiedToolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(projectedToolsByQualifiedName.get(qualifiedToolName));
    }

    @Override
    public List<AgentTool> specs() {
        return toolSpecs;
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

        McpProjectedTool projectedTool = projectedTool(call.toolName())
                .orElse(null);
        if (projectedTool == null) {
            return ToolOutcome.result(ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName()));
        }

        try {
            JsonObject remoteResult = session.callTool(projectedTool.remoteToolName(), call.argsJson());
            JsonObject normalizedResult = normalizeResultEnvelope(projectedTool, remoteResult);
            String payloadJson = JsonSupport.GSON.toJson(normalizedResult);
            if (isErrorResult(remoteResult)) {
                return ToolOutcome.result(ToolResult.error(
                        payloadJson,
                        DEFAULT_REMOTE_ERROR_CODE,
                        resolveRemoteErrorMessage(normalizedResult)
                ));
            }
            return ToolOutcome.result(ToolResult.ok(payloadJson));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ToolOutcome.result(ToolResult.error("invalid_args", requireMessage(illegalArgumentException,
                    "MCP tool arguments are invalid.")));
        } catch (McpTransportException transportException) {
            return ToolOutcome.result(ToolResult.error(
                    transportException.failureCode(),
                    transportException.failureMessage()
            ));
        } catch (RuntimeException runtimeException) {
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }
    }

    @Override
    public void close() {
        session.close();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private static JsonObject normalizeResultEnvelope(McpProjectedTool projectedTool, JsonObject remoteResult) {
        JsonObject resultObject = remoteResult == null ? new JsonObject() : remoteResult;
        JsonObject normalized = new JsonObject();
        normalized.addProperty("serverAlias", projectedTool.serverAlias());
        normalized.addProperty("qualifiedTool", projectedTool.qualifiedToolName());
        normalized.addProperty("remoteTool", projectedTool.remoteToolName());
        normalized.addProperty("isError", isErrorResult(resultObject));
        normalized.add("textContent", extractTextContent(resultObject));
        normalized.add("structuredContent", extractStructuredContent(resultObject));
        normalized.add("unsupportedContentTypes", extractUnsupportedContentTypes(resultObject));
        return normalized;
    }

    private static boolean isErrorResult(JsonObject remoteResult) {
        if (remoteResult == null || !remoteResult.has("isError")) {
            return false;
        }
        JsonElement isError = remoteResult.get("isError");
        return isError != null
                && isError.isJsonPrimitive()
                && isError.getAsJsonPrimitive().isBoolean()
                && isError.getAsBoolean();
    }

    private static JsonArray extractTextContent(JsonObject remoteResult) {
        JsonArray textContent = new JsonArray();
        JsonArray content = remoteResult == null ? null : asArray(remoteResult.get("content"));
        if (content == null) {
            return textContent;
        }
        for (JsonElement entry : content) {
            JsonObject contentObject = asObject(entry);
            if (contentObject == null) {
                continue;
            }
            String type = getString(contentObject, "type");
            if (!"text".equals(type)) {
                continue;
            }
            String text = getString(contentObject, "text");
            if (text != null && !text.isBlank()) {
                textContent.add(text);
            }
        }
        return textContent;
    }

    private static JsonElement extractStructuredContent(JsonObject remoteResult) {
        if (remoteResult == null || !remoteResult.has("structuredContent")) {
            return JsonNull.INSTANCE;
        }
        JsonElement structuredContent = remoteResult.get("structuredContent");
        return structuredContent == null ? JsonNull.INSTANCE : structuredContent.deepCopy();
    }

    private static JsonArray extractUnsupportedContentTypes(JsonObject remoteResult) {
        LinkedHashMap<String, Boolean> unsupportedTypes = new LinkedHashMap<>();
        JsonArray content = remoteResult == null ? null : asArray(remoteResult.get("content"));
        if (content != null) {
            for (JsonElement entry : content) {
                JsonObject contentObject = asObject(entry);
                if (contentObject == null) {
                    unsupportedTypes.put("unknown", Boolean.TRUE);
                    continue;
                }

                String type = getString(contentObject, "type");
                if ("text".equals(type)) {
                    String text = getString(contentObject, "text");
                    if (text == null || text.isBlank()) {
                        unsupportedTypes.put("text", Boolean.TRUE);
                    }
                    continue;
                }

                unsupportedTypes.put(type == null || type.isBlank() ? "unknown" : type, Boolean.TRUE);
            }
        }

        JsonArray unsupportedContentTypes = new JsonArray();
        for (String unsupportedType : unsupportedTypes.keySet()) {
            unsupportedContentTypes.add(unsupportedType);
        }
        return unsupportedContentTypes;
    }

    private static String resolveRemoteErrorMessage(JsonObject normalizedResult) {
        JsonArray textContent = asArray(normalizedResult.get("textContent"));
        if (textContent != null) {
            for (JsonElement entry : textContent) {
                if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    String text = entry.getAsString();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        JsonElement structuredContent = normalizedResult.get("structuredContent");
        String structuredMessage = extractStructuredMessage(structuredContent);
        if (structuredMessage != null && !structuredMessage.isBlank()) {
            return structuredMessage;
        }

        JsonArray unsupportedContentTypes = asArray(normalizedResult.get("unsupportedContentTypes"));
        if (unsupportedContentTypes != null && !unsupportedContentTypes.isEmpty()) {
            return "MCP tool reported only unsupported content.";
        }
        return "MCP tool reported an error.";
    }

    private static String extractStructuredMessage(JsonElement structuredContent) {
        if (structuredContent == null || structuredContent.isJsonNull()) {
            return null;
        }
        if (structuredContent.isJsonPrimitive() && structuredContent.getAsJsonPrimitive().isString()) {
            String message = structuredContent.getAsString();
            return message == null || message.isBlank() ? null : message;
        }
        JsonObject structuredObject = asObject(structuredContent);
        if (structuredObject == null) {
            return null;
        }
        String message = getString(structuredObject, "message");
        if (message != null && !message.isBlank()) {
            return message;
        }
        String error = getString(structuredObject, "error");
        if (error != null && !error.isBlank()) {
            return error;
        }
        return null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String fieldName) {
        if (object == null || fieldName == null || !object.has(fieldName)) {
            return null;
        }
        JsonElement value = object.get(fieldName);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (UnsupportedOperationException exception) {
            return null;
        }
    }

    private static String requireMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallback;
        }
        return throwable.getMessage();
    }
}
