package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.controlnet.mineagent.common.testing.TimeoutUtility;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;
import space.controlnet.mineagent.core.tools.mcp.McpConfig;
import space.controlnet.mineagent.core.tools.mcp.McpConfigParser;
import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;
import space.controlnet.mineagent.core.tools.mcp.McpTransportKind;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class McpEndToEndNamespacedRegressionTest {
    private static final Duration RELOAD_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(500);
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final String TOOL_PREFIX = "mcp.task9e2e";
    private static final String DROP_ALIAS = "task9e2e-drop";
    private static final String HTTP_ALIAS = "task9e2e-http";
    private static final String OLD_ALIAS = "task9e2e-old";
    private static final String SLOW_ALIAS = "task9e2e-slow";
    private static final String STDIO_ALIAS = "task9e2e-stdio";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cleanupRuntime();
    }

    @AfterEach
    void tearDown() {
        cleanupRuntime();
    }

    @Test
    void task9e2e_namespacedStdioAndHttpTools_coexistThroughRuntimeRegistryAndInvocation() throws Exception {
        try (StdioFixture stdioFixture = StdioFixture.create(
                tempDir,
                STDIO_ALIAS,
                "success",
                "search",
                "Search MCP docs over stdio"
        );
             HttpFixture httpFixture = HttpFixture.create(
                     "success",
                     HTTP_ALIAS,
                     "search",
                     "Search MCP docs over http"
             )) {
            Path configRoot = tempDir.resolve("config-end-to-end-coexistence");
            writeConfig(configRoot, orderedServers(
                    STDIO_ALIAS, stdioFixture.config(),
                    HTTP_ALIAS, httpFixture.config()
            ));

            McpRuntimeManager.reload(configRoot, shortTimeoutSessionFactory());
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(STDIO_ALIAS, HTTP_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of(
                    qualifiedToolName(HTTP_ALIAS),
                    qualifiedToolName(STDIO_ALIAS)
            ));

            ToolOutcome stdioOutcome = executeSearch(STDIO_ALIAS, "stdio docs");
            ToolOutcome httpOutcome = executeSearch(HTTP_ALIAS, "http docs");

            assertNormalizedSuccess(
                    "task9e2e/stdio-success",
                    stdioOutcome,
                    STDIO_ALIAS,
                    "stdio",
                    "search result via stdio"
            );
            assertNormalizedSuccess(
                    "task9e2e/http-success",
                    httpOutcome,
                    HTTP_ALIAS,
                    "http",
                    "search result via http"
            );

            assertEquals(
                    List.of("initialize", "notifications/initialized", "tools/list", "tools/call"),
                    httpFixture.requestSnapshots().stream().map(RequestSnapshot::method).toList()
            );
        }
    }

    @Test
    void task9e2e_reloadRemovalAndFailureCalls_keepNamespacedSnapshotsDeterministic() throws Exception {
        try (StdioFixture oldFixture = StdioFixture.create(
                tempDir,
                OLD_ALIAS,
                "success",
                "search",
                "Search MCP docs before reload"
        );
             StdioFixture slowFixture = StdioFixture.create(
                     tempDir,
                     SLOW_ALIAS,
                     "timeout_on_call",
                     "search",
                     "Search MCP docs slowly"
             );
             HttpFixture dropFixture = HttpFixture.create(
                     "disconnect_on_call",
                     DROP_ALIAS,
                     "search",
                     "Search MCP docs unreliably"
             );
             HttpFixture httpFixture = HttpFixture.create(
                     "success",
                     HTTP_ALIAS,
                     "search",
                     "Search MCP docs over http"
             )) {
            Path configRoot = tempDir.resolve("config-end-to-end-reload-failures");
            writeConfig(configRoot, orderedServers(
                    OLD_ALIAS, oldFixture.config(),
                    SLOW_ALIAS, slowFixture.config(),
                    DROP_ALIAS, dropFixture.config(),
                    HTTP_ALIAS, httpFixture.config()
            ));

            McpRuntimeManager.reload(configRoot, shortTimeoutSessionFactory());
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            List<String> initialOwnedToolNames = List.of(
                    qualifiedToolName(DROP_ALIAS),
                    qualifiedToolName(HTTP_ALIAS),
                    qualifiedToolName(OLD_ALIAS),
                    qualifiedToolName(SLOW_ALIAS)
            );
            assertOwnedToolNames(initialOwnedToolNames);

            assertNormalizedSuccess(
                    "task9e2e/old-success",
                    executeSearch(OLD_ALIAS, "before reload"),
                    OLD_ALIAS,
                    "stdio",
                    "search result via stdio"
            );
            assertFailure(
                    "task9e2e/slow-timeout",
                    executeSearch(SLOW_ALIAS, "slow request"),
                    "tool_timeout",
                    "tool execution timeout"
            );
            assertFailure(
                    "task9e2e/drop-disconnect",
                    executeSearch(DROP_ALIAS, "disconnect request"),
                    "tool_execution_failed",
                    "tool execution failed"
            );
            assertNormalizedSuccess(
                    "task9e2e/http-still-healthy",
                    executeSearch(HTTP_ALIAS, "healthy request"),
                    HTTP_ALIAS,
                    "http",
                    "search result via http"
            );
            assertOwnedToolNames(initialOwnedToolNames);

            writeConfig(configRoot, orderedServers(
                    HTTP_ALIAS, httpFixture.config(),
                    SLOW_ALIAS, slowFixture.config()
            ));

            McpRuntimeManager.reload(configRoot, shortTimeoutSessionFactory());
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(HTTP_ALIAS, SLOW_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of(
                    qualifiedToolName(HTTP_ALIAS),
                    qualifiedToolName(SLOW_ALIAS)
            ));
            assertNull(ToolRegistry.getToolSpec(qualifiedToolName(OLD_ALIAS)));
            assertNull(ToolRegistry.getToolSpec(qualifiedToolName(DROP_ALIAS)));
            assertFailure(
                    "task9e2e/removed-alias-unknown-tool",
                    executeSearch(OLD_ALIAS, "removed request"),
                    "unknown_tool",
                    "Unknown tool: " + qualifiedToolName(OLD_ALIAS)
            );
            assertNormalizedSuccess(
                    "task9e2e/http-after-reload",
                    executeSearch(HTTP_ALIAS, "after reload"),
                    HTTP_ALIAS,
                    "http",
                    "search result via http"
            );
            assertFailure(
                    "task9e2e/slow-after-reload-timeout",
                    executeSearch(SLOW_ALIAS, "still slow"),
                    "tool_timeout",
                    "tool execution timeout"
            );

            TimeoutUtility.await(
                    "task9e2e/reload-old-runtime-closed",
                    Duration.ofSeconds(5),
                    () -> Files.exists(oldFixture.closeMarker())
            );
        }
    }

    private static McpRuntimeManager.SessionFactory shortTimeoutSessionFactory() {
        return new McpRuntimeManager.SessionFactory() {
            @Override
            public McpClientSession open(String serverAlias, McpServerConfig serverConfig) throws McpTransportException {
                if (serverConfig.type() == McpTransportKind.STDIO) {
                    return new StdioSessionAdapter(McpStdioClientSession.open(
                            serverAlias,
                            serverConfig,
                            REQUEST_TIMEOUT,
                            MAX_RESPONSE_BYTES
                    ));
                }
                if (serverConfig.type() == McpTransportKind.HTTP) {
                    return new HttpSessionAdapter(McpHttpClientSession.open(
                            serverAlias,
                            serverConfig,
                            REQUEST_TIMEOUT,
                            MAX_RESPONSE_BYTES
                    ));
                }
                throw McpTransportException.executionFailed(new IllegalStateException(
                        "Unsupported MCP transport for server '" + serverAlias + "'."
                ));
            }
        };
    }

    private static void cleanupRuntime() {
        McpRuntimeManager.clear();
        McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);
        ToolRegistry.unregisterByPrefix(TOOL_PREFIX);
    }

    private static LinkedHashMap<String, McpServerConfig> orderedServers(Object... values) {
        LinkedHashMap<String, McpServerConfig> ordered = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            ordered.put((String) values[index], (McpServerConfig) values[index + 1]);
        }
        return ordered;
    }

    private static void writeConfig(Path configRoot, Map<String, McpServerConfig> servers) throws Exception {
        Path configPath = McpConfigLoader.resolveConfigPath(configRoot);
        Files.createDirectories(configPath.getParent());
        try (var writer = Files.newBufferedWriter(configPath)) {
            McpConfigParser.writeJson(writer, new McpConfig(servers));
        }
    }

    private static ToolOutcome executeSearch(String serverAlias, String query) {
        return ToolRegistry.executeTool(
                Optional.empty(),
                new ToolCall(qualifiedToolName(serverAlias), "{\"query\":\"" + query + "\"}"),
                true
        );
    }

    private static String qualifiedToolName(String serverAlias) {
        return McpSchemaMapper.qualifiedToolName(serverAlias, "search");
    }

    private static void assertOwnedToolNames(List<String> expectedToolNames) {
        List<String> actualToolNames = new ArrayList<>();
        for (AgentTool tool : ToolRegistry.getToolSpecs()) {
            if (tool == null || tool.name() == null || !tool.name().startsWith(TOOL_PREFIX)) {
                continue;
            }
            actualToolNames.add(tool.name());
        }
        assertEquals(expectedToolNames, actualToolNames);
    }

    private static void assertNormalizedSuccess(String assertionName, ToolOutcome outcome, String expectedAlias,
                                                String expectedTransport, String expectedTextContent) {
        ToolResult result = requireResult(assertionName, outcome);
        assertTrue(result.success(), assertionName + " -> result must be successful");
        assertNull(result.error(), assertionName + " -> error must be null");

        JsonObject payload = requireJsonObject(assertionName + " -> payload", result.payloadJson());
        assertEquals(expectedAlias, payload.get("serverAlias").getAsString(), assertionName + " -> server alias");
        assertEquals(qualifiedToolName(expectedAlias), payload.get("qualifiedTool").getAsString(),
                assertionName + " -> qualified tool");
        assertEquals("search", payload.get("remoteTool").getAsString(), assertionName + " -> remote tool");
        assertFalse(payload.get("isError").getAsBoolean(), assertionName + " -> result should not be an error");
        assertEquals(List.of(expectedTextContent), stringValues(payload.getAsJsonArray("textContent")),
                assertionName + " -> text content");

        JsonObject structuredContent = payload.getAsJsonObject("structuredContent");
        assertNotNull(structuredContent, assertionName + " -> structured content must be present");
        assertEquals(expectedTransport, structuredContent.get("transport").getAsString(),
                assertionName + " -> transport marker");
        assertEquals(expectedAlias, structuredContent.get("serverAlias").getAsString(),
                assertionName + " -> structured alias marker");
        assertTrue(structuredContent.get("initializedSeen").getAsBoolean(),
                assertionName + " -> initialized notification should be observed");
    }

    private static void assertFailure(String assertionName, ToolOutcome outcome, String expectedCode,
                                      String expectedMessage) {
        ToolResult result = requireResult(assertionName, outcome);
        assertFalse(result.success(), assertionName + " -> result must be a failure");
        assertNotNull(result.error(), assertionName + " -> error must be present");
        assertEquals(expectedCode, result.error().code(), assertionName + " -> error code");
        assertEquals(expectedMessage, result.error().message(), assertionName + " -> error message");
    }

    private static ToolResult requireResult(String assertionName, ToolOutcome outcome) {
        assertNotNull(outcome, assertionName + " -> outcome must not be null");
        assertNotNull(outcome.result(), assertionName + " -> result must not be null");
        return outcome.result();
    }

    private static JsonObject requireJsonObject(String assertionName, String rawJson) {
        JsonElement parsed = JsonParser.parseString(rawJson == null ? "null" : rawJson);
        assertTrue(parsed.isJsonObject(), assertionName + " -> expected a JSON object payload");
        return parsed.getAsJsonObject();
    }

    private static List<String> stringValues(com.google.gson.JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private record StdioFixture(McpServerConfig config, Path closeMarker) implements AutoCloseable {
        private static final String SERVER_SCRIPT = """
                import json
                import os
                import sys
                import time

                MODE = os.environ.get(\"CHATMC_FIXTURE_MODE\", \"success\")
                SERVER_ALIAS = os.environ.get(\"CHATMC_SERVER_ALIAS\", \"stdio\")
                CLOSE_MARKER = os.environ.get(\"CHATMC_CLOSE_MARKER\", \"\")
                TOOL_NAME = os.environ.get(\"CHATMC_TOOL_NAME\", \"search\")
                TOOL_DESCRIPTION = os.environ.get(\"CHATMC_TOOL_DESCRIPTION\", \"Fixture tool\")
                initialized_seen = False

                def send(payload):
                    sys.stdout.write(json.dumps(payload, separators=(\",\", \":\")) + \"\\n\")
                    sys.stdout.flush()

                def list_response(message):
                    return {
                        \"jsonrpc\": \"2.0\",
                        \"id\": message[\"id\"],
                        \"result\": {
                            \"tools\": [
                                {
                                    \"name\": TOOL_NAME,
                                    \"description\": TOOL_DESCRIPTION,
                                    \"inputSchema\": {
                                        \"type\": \"object\",
                                        \"properties\": {
                                            \"query\": {\"type\": \"string\"}
                                        },
                                        \"required\": [\"query\"]
                                    },
                                    \"annotations\": {
                                        \"readOnlyHint\": True
                                    }
                                }
                            ]
                        }
                    }

                def call_response(message):
                    return {
                        \"jsonrpc\": \"2.0\",
                        \"id\": message[\"id\"],
                        \"result\": {
                            \"content\": [
                                {
                                    \"type\": \"text\",
                                    \"text\": \"search result via stdio\"
                                }
                            ],
                            \"structuredContent\": {
                                \"transport\": \"stdio\",
                                \"serverAlias\": SERVER_ALIAS,
                                \"initializedSeen\": initialized_seen
                            },
                            \"isError\": False
                        }
                    }

                try:
                    for raw_line in sys.stdin:
                        line = raw_line.rstrip(\"\\n\")
                        if not line:
                            continue
                        message = json.loads(line)
                        method = message.get(\"method\")

                        if method == \"initialize\":
                            send({
                                \"jsonrpc\": \"2.0\",
                                \"id\": message[\"id\"],
                                \"result\": {
                                    \"protocolVersion\": message[\"params\"][\"protocolVersion\"],
                                    \"capabilities\": {\"tools\": {}},
                                    \"serverInfo\": {\"name\": \"fixture\", \"version\": \"1.0.0\"}
                                }
                            })
                        elif method == \"notifications/initialized\":
                            initialized_seen = True
                        elif method == \"tools/list\":
                            send(list_response(message))
                        elif method == \"tools/call\":
                            if MODE == \"timeout_on_call\":
                                time.sleep(0.5)
                            send(call_response(message))
                finally:
                    if CLOSE_MARKER:
                        with open(CLOSE_MARKER, \"w\", encoding=\"utf-8\") as marker:
                            marker.write(\"closed\")
                """;

        private static StdioFixture create(Path root, String serverAlias, String mode, String toolName,
                                           String toolDescription) throws IOException {
            Path script = root.resolve(serverAlias + "-fixture-server.py");
            Files.writeString(script, SERVER_SCRIPT);
            Path closeMarker = root.resolve(serverAlias + ".close.marker");
            McpServerConfig config = McpServerConfig.stdio(
                    "python3",
                    List.of(script.toString()),
                    Map.of(
                            "CHATMC_FIXTURE_MODE", mode,
                            "CHATMC_SERVER_ALIAS", serverAlias,
                            "CHATMC_CLOSE_MARKER", closeMarker.toString(),
                            "CHATMC_TOOL_NAME", toolName,
                            "CHATMC_TOOL_DESCRIPTION", toolDescription
                    ),
                    Optional.of(root.toString())
            );
            return new StdioFixture(config, closeMarker);
        }

        @Override
        public void close() {
        }
    }

    private record RequestSnapshot(String method, String sessionHeader, String protocolHeader) {
    }

    private static final class HttpFixture implements AutoCloseable {
        private final String mode;
        private final String serverAlias;
        private final String sessionId;
        private final String toolName;
        private final String toolDescription;
        private final HttpServer server;
        private final ExecutorService serverExecutor;
        private final AtomicBoolean initializedSeen = new AtomicBoolean();
        private final List<RequestSnapshot> requestSnapshots = new CopyOnWriteArrayList<>();

        private HttpFixture(String mode, String serverAlias, String sessionId, String toolName, String toolDescription,
                            HttpServer server, ExecutorService serverExecutor) {
            this.mode = mode;
            this.serverAlias = serverAlias;
            this.sessionId = sessionId;
            this.toolName = toolName;
            this.toolDescription = toolDescription;
            this.server = server;
            this.serverExecutor = serverExecutor;
        }

        private static HttpFixture create(String mode, String serverAlias, String toolName, String toolDescription)
                throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService serverExecutor = java.util.concurrent.Executors.newCachedThreadPool();
            HttpFixture fixture = new HttpFixture(
                    mode,
                    serverAlias,
                    "session-" + serverAlias + "-" + UUID.randomUUID(),
                    toolName,
                    toolDescription,
                    server,
                    serverExecutor
            );
            server.createContext("/mcp", fixture::handle);
            server.setExecutor(serverExecutor);
            server.start();
            return fixture;
        }

        private McpServerConfig config() {
            return McpServerConfig.http(endpoint().toString());
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        }

        private List<RequestSnapshot> requestSnapshots() {
            return new ArrayList<>(requestSnapshots);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendStatus(exchange, 405);
                    return;
                }

                JsonObject request = parseRequest(exchange);
                String method = request.get("method").getAsString();
                requestSnapshots.add(new RequestSnapshot(
                        method,
                        exchange.getRequestHeaders().getFirst("MCP-Session-Id"),
                        exchange.getRequestHeaders().getFirst("MCP-Protocol-Version")
                ));

                if ("initialize".equals(method)) {
                    sendJson(exchange, initializeResponse(request), true);
                    return;
                }
                if ("notifications/initialized".equals(method)) {
                    initializedSeen.set(true);
                    sendStatus(exchange, 202);
                    return;
                }
                if ("tools/list".equals(method)) {
                    sendJson(exchange, listResponse(request), false);
                    return;
                }
                if ("tools/call".equals(method)) {
                    if ("disconnect_on_call".equals(mode)) {
                        sendTruncatedJson(exchange, request);
                        return;
                    }
                    sendJson(exchange, callResponse(exchange, request), false);
                    return;
                }

                sendStatus(exchange, 404);
            } finally {
                exchange.close();
            }
        }

        private static JsonObject parseRequest(HttpExchange exchange) throws IOException {
            JsonElement element = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            return element.getAsJsonObject();
        }

        private JsonObject initializeResponse(JsonObject request) {
            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", request.getAsJsonObject("params").get("protocolVersion").getAsString());
            result.add("capabilities", new JsonObject());

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("name", "fixture");
            serverInfo.addProperty("version", "1.0.0");
            result.add("serverInfo", serverInfo);

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", request.get("id"));
            response.add("result", result);
            return response;
        }

        private JsonObject listResponse(JsonObject request) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", toolName);
            tool.addProperty("description", toolDescription);

            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject query = new JsonObject();
            query.addProperty("type", "string");
            properties.add("query", query);
            inputSchema.add("properties", properties);
            inputSchema.add("required", JsonParser.parseString("[\"query\"]"));
            tool.add("inputSchema", inputSchema);

            JsonObject annotations = new JsonObject();
            annotations.addProperty("readOnlyHint", true);
            tool.add("annotations", annotations);

            JsonObject result = new JsonObject();
            result.add("tools", JsonParser.parseString("[]").getAsJsonArray());
            result.getAsJsonArray("tools").add(tool);

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", request.get("id"));
            response.add("result", result);
            return response;
        }

        private JsonObject callResponse(HttpExchange exchange, JsonObject request) {
            JsonObject contentEntry = new JsonObject();
            contentEntry.addProperty("type", "text");
            contentEntry.addProperty("text", "search result via http");

            JsonObject structuredContent = new JsonObject();
            structuredContent.addProperty("transport", "http");
            structuredContent.addProperty("serverAlias", serverAlias);
            structuredContent.addProperty("initializedSeen", initializedSeen.get());
            structuredContent.addProperty("sessionHeader", exchange.getRequestHeaders().getFirst("MCP-Session-Id"));
            structuredContent.addProperty("protocolHeader", exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));

            JsonObject result = new JsonObject();
            result.add("content", JsonParser.parseString("[]").getAsJsonArray());
            result.getAsJsonArray("content").add(contentEntry);
            result.add("structuredContent", structuredContent);
            result.addProperty("isError", false);

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", request.get("id"));
            response.add("result", result);
            return response;
        }

        private void sendJson(HttpExchange exchange, JsonObject payload, boolean includeSessionHeader) throws IOException {
            byte[] responseBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (includeSessionHeader) {
                exchange.getResponseHeaders().add("MCP-Session-Id", sessionId);
            }
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }

        private static void sendStatus(HttpExchange exchange, int statusCode) throws IOException {
            exchange.sendResponseHeaders(statusCode, -1L);
        }

        private static void sendTruncatedJson(HttpExchange exchange, JsonObject request) throws IOException {
            byte[] responseBytes = ("{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id").getAsLong()
                    + ",\"result\":{").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length + 32L);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
                outputStream.flush();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    private record StdioSessionAdapter(McpStdioClientSession delegate) implements McpClientSession {
        @Override
        public List<McpSchemaMapper.McpRemoteTool> listTools() throws McpTransportException {
            return delegate.listTools();
        }

        @Override
        public JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
            return delegate.callTool(remoteToolName, argumentsJson);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private record HttpSessionAdapter(McpHttpClientSession delegate) implements McpClientSession {
        @Override
        public List<McpSchemaMapper.McpRemoteTool> listTools() throws McpTransportException {
            return delegate.listTools();
        }

        @Override
        public JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
            return delegate.callTool(remoteToolName, argumentsJson);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
