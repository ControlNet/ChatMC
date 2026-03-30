package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import space.controlnet.mineagent.common.tools.mcp.McpSchemaMapper.McpRemoteTool;
import space.controlnet.mineagent.common.tools.mcp.transport.McpStdioTransport;
import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public final class McpStdioClientSession implements AutoCloseable {
    static final String PROTOCOL_VERSION = "2025-11-25";
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_RESPONSE_BYTES = 1_048_576;

    private final String serverAlias;
    private final McpStdioTransport transport;

    private McpStdioClientSession(String serverAlias, McpStdioTransport transport) {
        this.serverAlias = serverAlias;
        this.transport = transport;
    }

    public static McpStdioClientSession open(String serverAlias, McpServerConfig config) throws McpTransportException {
        return open(serverAlias, config, DEFAULT_REQUEST_TIMEOUT, DEFAULT_MAX_RESPONSE_BYTES);
    }

    static McpStdioClientSession open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                      int maxResponseBytes) throws McpTransportException {
        McpStdioTransport transport = McpStdioTransport.open(serverAlias, config, requestTimeout, maxResponseBytes);
        return initializeSession(serverAlias, transport);
    }

    static McpStdioClientSession open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                      int maxResponseBytes, ExecutorService ioExecutor) throws McpTransportException {
        McpStdioTransport transport = McpStdioTransport.open(serverAlias, config, requestTimeout, maxResponseBytes,
                ioExecutor);
        return initializeSession(serverAlias, transport);
    }

    public List<McpRemoteTool> listTools() throws McpTransportException {
        List<McpRemoteTool> tools = new ArrayList<>();
        String cursor = null;
        do {
            JsonObject params = new JsonObject();
            if (cursor != null) {
                params.addProperty("cursor", cursor);
            }

            JsonObject result = requireResultObject(transport.request("tools/list", params), "tools/list");
            JsonArray toolsArray = requireArray(result, "tools", "tools/list");
            for (JsonElement toolElement : toolsArray) {
                if (!toolElement.isJsonObject()) {
                    throw McpTransportException.executionFailed(new IllegalStateException(
                            "MCP tools/list returned a non-object tool definition."
                    ));
                }
                tools.add(parseRemoteTool(toolElement.getAsJsonObject()));
            }
            cursor = optionalString(result.get("nextCursor"));
        } while (cursor != null && !cursor.isBlank());
        return List.copyOf(tools);
    }

    public JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
        if (remoteToolName == null || remoteToolName.isBlank()) {
            throw new IllegalArgumentException("MCP remote tool name is required.");
        }

        JsonObject params = new JsonObject();
        params.addProperty("name", remoteToolName);
        params.add("arguments", parseArguments(argumentsJson));
        return requireResultObject(transport.request("tools/call", params), "tools/call");
    }

    @Override
    public void close() {
        transport.close();
    }

    private static McpStdioClientSession initializeSession(String serverAlias, McpStdioTransport transport)
            throws McpTransportException {
        McpStdioClientSession session = new McpStdioClientSession(serverAlias, transport);
        try {
            session.initialize();
            return session;
        } catch (McpTransportException transportException) {
            session.close();
            throw transportException;
        }
    }

    private void initialize() throws McpTransportException {
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", PROTOCOL_VERSION);
        params.add("capabilities", new JsonObject());

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "MineAgent");
        clientInfo.addProperty("version", "dev");
        params.add("clientInfo", clientInfo);

        JsonObject initializeResult = requireResultObject(transport.request("initialize", params), "initialize");
        String negotiatedProtocol = requireString(initializeResult, "protocolVersion", "initialize");
        if (!PROTOCOL_VERSION.equals(negotiatedProtocol)) {
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "Unsupported MCP protocol version '" + negotiatedProtocol + "' from server '" + serverAlias + "'."
            ));
        }

        transport.notify("notifications/initialized", null);
    }

    private static McpRemoteTool parseRemoteTool(JsonObject toolObject) throws McpTransportException {
        String name = requireString(toolObject, "name", "tools/list.tool");
        Optional<String> description = Optional.ofNullable(optionalString(toolObject.get("description")))
                .filter(value -> !value.isBlank());

        JsonElement inputSchemaElement = toolObject.get("inputSchema");
        if (inputSchemaElement == null || !inputSchemaElement.isJsonObject()) {
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "MCP tool '" + name + "' is missing a valid inputSchema object."
            ));
        }

        Optional<Boolean> readOnlyHint = Optional.empty();
        JsonElement annotationsElement = toolObject.get("annotations");
        if (annotationsElement != null && annotationsElement.isJsonObject()) {
            JsonElement readOnlyHintElement = annotationsElement.getAsJsonObject().get("readOnlyHint");
            if (readOnlyHintElement != null && readOnlyHintElement.isJsonPrimitive()
                    && readOnlyHintElement.getAsJsonPrimitive().isBoolean()) {
                readOnlyHint = Optional.of(readOnlyHintElement.getAsBoolean());
            }
        }

        return new McpRemoteTool(name, description, inputSchemaElement, readOnlyHint);
    }

    private static JsonObject requireResultObject(JsonObject response, String methodName) throws McpTransportException {
        JsonElement errorElement = response.get("error");
        if (errorElement != null && errorElement.isJsonObject()) {
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "MCP method '" + methodName + "' returned a JSON-RPC error response."
            ));
        }

        JsonElement resultElement = response.get("result");
        if (resultElement == null || !resultElement.isJsonObject()) {
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "MCP method '" + methodName + "' returned no JSON object result."
            ));
        }
        return resultElement.getAsJsonObject().deepCopy();
    }

    private static JsonArray requireArray(JsonObject parent, String fieldName, String methodName)
            throws McpTransportException {
        JsonElement element = parent.get(fieldName);
        if (element == null || !element.isJsonArray()) {
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "MCP method '" + methodName + "' returned no array field '" + fieldName + "'."
            ));
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject parent, String fieldName, String context)
            throws McpTransportException {
        String value = optionalString(parent.get(fieldName));
        if (value != null && !value.isBlank()) {
            return value;
        }
        throw McpTransportException.executionFailed(new IllegalStateException(
                "MCP response for '" + context + "' is missing string field '" + fieldName + "'."
        ));
    }

    private static JsonObject parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(argumentsJson);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("MCP tool arguments must be a JSON object.");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException parseException) {
            throw new IllegalArgumentException("MCP tool arguments must be valid JSON.", parseException);
        }
    }

    private static String optionalString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }
}
