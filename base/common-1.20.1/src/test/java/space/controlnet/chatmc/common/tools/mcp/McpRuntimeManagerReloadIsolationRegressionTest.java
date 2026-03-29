package space.controlnet.chatmc.common.tools.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.controlnet.chatmc.common.testing.TimeoutUtility;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.mcp.McpConfig;
import space.controlnet.chatmc.core.tools.mcp.McpConfigParser;
import space.controlnet.chatmc.core.tools.mcp.McpServerConfig;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class McpRuntimeManagerReloadIsolationRegressionTest {
    private static final Duration RELOAD_TIMEOUT = Duration.ofSeconds(10);
    private static final String TOOL_PREFIX = "mcp.task17";
    private static final String DOCS_ALIAS = "task17docs";
    private static final String BROKEN_ALIAS = "task17broken";
    private static final String WIKI_ALIAS = "task17wiki";
    private static final String FRESH_ALIAS = "task17fresh";

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
    void task17_reload_registersHealthyServersAndSkipsBrokenOnes() throws Exception {
        try (HttpFixture docsFixture = HttpFixture.create("task17-docs", "search", "Search docs")) {
            Path configRoot = tempDir.resolve("config-healthy-and-broken");
            writeConfig(configRoot, orderedServers(
                    DOCS_ALIAS, docsFixture.config(),
                    BROKEN_ALIAS, brokenStdioConfig()
            ));

            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(DOCS_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of("mcp." + DOCS_ALIAS + ".search"));
            assertNotNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));
            assertNull(ToolRegistry.getToolSpec("mcp." + BROKEN_ALIAS + ".search"));
        }
    }

    @Test
    void task17_reload_unregistersStaleToolsBeforeRegisteringNewSet() throws Exception {
        try (StdioFixture docsFixture = StdioFixture.create(tempDir, "task17-docs-v1", "search", "Search docs");
             StdioFixture wikiFixture = StdioFixture.create(tempDir, "task17-wiki-v1", "fetch_page", "Fetch wiki page");
             HttpFixture freshFixture = HttpFixture.create("task17-fresh", "browse", "Browse fresh docs")) {
            Path configRoot = tempDir.resolve("config-stale-removal");
            writeConfig(configRoot, orderedServers(
                    DOCS_ALIAS, docsFixture.config(),
                    WIKI_ALIAS, wikiFixture.config()
            ));

            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);
            assertOwnedToolNames(List.of(
                    "mcp." + DOCS_ALIAS + ".search",
                    "mcp." + WIKI_ALIAS + ".fetch_page"
            ));

            writeConfig(configRoot, orderedServers(
                    DOCS_ALIAS, brokenStdioConfig(),
                    FRESH_ALIAS, freshFixture.config()
            ));

            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(DOCS_ALIAS, FRESH_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of(
                    "mcp." + DOCS_ALIAS + ".search",
                    "mcp." + FRESH_ALIAS + ".browse"
            ));
            assertNotNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));
            assertNotNull(ToolRegistry.getToolSpec("mcp." + FRESH_ALIAS + ".browse"));
            assertNull(ToolRegistry.getToolSpec("mcp." + WIKI_ALIAS + ".fetch_page"));

            TimeoutUtility.await("task17/stale-alias-close", Duration.ofSeconds(5),
                    () -> Files.exists(wikiFixture.closeMarker()));
            assertTrue(Files.notExists(docsFixture.closeMarker()),
                    "task17/reload-retains-old-docs-runtime -> expected old docs runtime to remain active");
        }
    }

    @Test
    void task17_clear_closesSessionsAndUnregistersProviders() throws Exception {
        try (StdioFixture docsFixture = StdioFixture.create(tempDir, "task17-clear", "search", "Search docs")) {
            Path configRoot = tempDir.resolve("config-clear");
            writeConfig(configRoot, orderedServers(DOCS_ALIAS, docsFixture.config()));

            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);
            assertNotNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));

            McpRuntimeManager.clear();

            assertEquals(List.of(), McpRuntimeManager.activeAliases());
            assertNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));
            TimeoutUtility.await("task17/clear-close", Duration.ofSeconds(5),
                    () -> Files.exists(docsFixture.closeMarker()));
        }
    }

    @Test
    void task17_invalidOrMissingReload_keepsPreviouslyHealthyProviders() throws Exception {
        try (HttpFixture docsFixture = HttpFixture.create("task17-invalid-missing", "search", "Search docs")) {
            Path configRoot = tempDir.resolve("config-invalid-missing");
            Path configPath = McpConfigLoader.resolveConfigPath(configRoot);
            writeConfig(configRoot, orderedServers(DOCS_ALIAS, docsFixture.config()));

            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);
            assertEquals(List.of(DOCS_ALIAS), McpRuntimeManager.activeAliases());
            assertNotNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));

            Files.writeString(configPath, "{not-valid-json", StandardCharsets.UTF_8);
            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(DOCS_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of("mcp." + DOCS_ALIAS + ".search"));

            Files.delete(configPath);
            McpRuntimeManager.reload(configRoot);
            McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

            assertEquals(List.of(DOCS_ALIAS), McpRuntimeManager.activeAliases());
            assertOwnedToolNames(List.of("mcp." + DOCS_ALIAS + ".search"));
            assertTrue(Files.exists(configPath), "task17/missing-reload-defaults-written -> expected defaults file");
        }
    }

    @Test
    void task17_reload_staleGeneration_discardsOlderDiscoveryAndClosesItBeforeRegisteringNewest() throws Exception {
        Path configRoot = tempDir.resolve("config-stale-generation");
        writeConfig(configRoot, orderedServers(DOCS_ALIAS, McpServerConfig.http("http://127.0.0.1:1/mcp")));

        CountDownLatch firstDiscoveryStarted = new CountDownLatch(1);
        CountDownLatch allowFirstDiscovery = new CountDownLatch(1);
        AtomicBoolean firstClosed = new AtomicBoolean(false);
        AtomicBoolean secondClosed = new AtomicBoolean(false);
        AtomicInteger openCount = new AtomicInteger();

        McpRuntimeManager.SessionFactory sessionFactory = (serverAlias, serverConfig) -> {
            int attempt = openCount.incrementAndGet();
            if (attempt == 1) {
                return new LatchControlledSession("search", firstDiscoveryStarted, allowFirstDiscovery, firstClosed);
            }
            return new LatchControlledSession("browse", null, null, secondClosed);
        };

        McpRuntimeManager.reload(configRoot, sessionFactory);
        assertTrue(firstDiscoveryStarted.await(5, TimeUnit.SECONDS),
                "task17/stale-generation/first-discovery-started -> timed out waiting for first discovery");

        McpRuntimeManager.reload(configRoot, sessionFactory);
        allowFirstDiscovery.countDown();
        McpRuntimeManager.awaitIdle(RELOAD_TIMEOUT);

        assertEquals(2, openCount.get());
        assertTrue(firstClosed.get(), "task17/stale-generation/first-session-closed -> expected stale discovery to close");
        assertTrue(!secondClosed.get(), "task17/stale-generation/second-session-open -> newest runtime should remain active");
        assertEquals(List.of(DOCS_ALIAS), McpRuntimeManager.activeAliases());
        assertNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".search"));
        assertNotNull(ToolRegistry.getToolSpec("mcp." + DOCS_ALIAS + ".browse"));
        assertOwnedToolNames(List.of("mcp." + DOCS_ALIAS + ".browse"));
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

    private static McpServerConfig brokenStdioConfig() {
        return McpServerConfig.stdio(
                "definitely-missing-chatmc-mcp-command",
                List.of(),
                Map.of(),
                Optional.empty()
        );
    }

    private static void writeConfig(Path configRoot, Map<String, McpServerConfig> servers) throws Exception {
        Path configPath = McpConfigLoader.resolveConfigPath(configRoot);
        Files.createDirectories(configPath.getParent());
        try (var writer = Files.newBufferedWriter(configPath)) {
            McpConfigParser.writeJson(writer, new McpConfig(servers));
        }
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

    private record StdioFixture(McpServerConfig config, Path closeMarker) implements AutoCloseable {
        private static final String SERVER_SCRIPT = """
                import json
                import os
                import sys

                CLOSE_MARKER = os.environ.get(\"CHATMC_CLOSE_MARKER\", \"\")
                TOOL_NAME = os.environ.get(\"CHATMC_TOOL_NAME\", \"search\")
                TOOL_DESCRIPTION = os.environ.get(\"CHATMC_TOOL_DESCRIPTION\", \"Fixture tool\")

                def send(payload):
                    sys.stdout.write(json.dumps(payload, separators=(\",\", \":\")) + \"\\n\")
                    sys.stdout.flush()

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
                        elif method == \"tools/list\":
                            send({
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
                            })
                finally:
                    if CLOSE_MARKER:
                        with open(CLOSE_MARKER, \"w\", encoding=\"utf-8\") as marker:
                            marker.write(\"closed\")
                """;

        private static StdioFixture create(Path root, String name, String toolName, String toolDescription)
                throws IOException {
            Path script = root.resolve(name + "-fixture.py");
            Files.writeString(script, SERVER_SCRIPT);
            Path closeMarker = root.resolve(name + ".close.marker");
            McpServerConfig config = McpServerConfig.stdio(
                    "python3",
                    List.of(script.toString()),
                    Map.of(
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

    private static final class HttpFixture implements AutoCloseable {
        private final HttpServer server;
        private final String toolName;
        private final String toolDescription;

        private HttpFixture(HttpServer server, String toolName, String toolDescription) {
            this.server = server;
            this.toolName = toolName;
            this.toolDescription = toolDescription;
        }

        private static HttpFixture create(String name, String toolName, String toolDescription) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            HttpFixture fixture = new HttpFixture(server, toolName, toolDescription);
            server.createContext("/mcp", exchange -> fixture.handle(name, exchange));
            server.start();
            return fixture;
        }

        private McpServerConfig config() {
            return McpServerConfig.http(endpoint().toString());
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        }

        private void handle(String name, HttpExchange exchange) throws IOException {
            try {
                JsonObject request = parseRequest(exchange);
                String method = requireMethod(request);
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendStatus(exchange, 405);
                    return;
                }
                if ("initialize".equals(method)) {
                    sendJson(exchange, initializeResponse(request));
                    return;
                }
                if ("notifications/initialized".equals(method)) {
                    sendStatus(exchange, 202);
                    return;
                }
                if ("tools/list".equals(method)) {
                    sendJson(exchange, toolsListResponse(request, name));
                    return;
                }
                sendStatus(exchange, 404);
            } finally {
                exchange.close();
            }
        }

        private JsonObject parseRequest(HttpExchange exchange) throws IOException {
            JsonElement element = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            return element.getAsJsonObject();
        }

        private static String requireMethod(JsonObject request) {
            return request.get("method").getAsString();
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

        private JsonObject toolsListResponse(JsonObject request, String name) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", toolName);
            tool.addProperty("description", toolDescription + " via " + name);

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

        private static void sendJson(HttpExchange exchange, JsonObject payload) throws IOException {
            byte[] responseBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }

        private static void sendStatus(HttpExchange exchange, int statusCode) throws IOException {
            exchange.sendResponseHeaders(statusCode, -1L);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class LatchControlledSession implements McpClientSession {
        private final String toolName;
        private final CountDownLatch started;
        private final CountDownLatch allowContinue;
        private final AtomicBoolean closed;

        private LatchControlledSession(String toolName, CountDownLatch started, CountDownLatch allowContinue,
                                       AtomicBoolean closed) {
            this.toolName = toolName;
            this.started = started;
            this.allowContinue = allowContinue;
            this.closed = closed;
        }

        @Override
        public List<McpSchemaMapper.McpRemoteTool> listTools() {
            if (started != null) {
                started.countDown();
            }
            if (allowContinue != null) {
                try {
                    if (!allowContinue.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("task17/stale-generation/list-tools-timeout -> timed out waiting to continue");
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("task17/stale-generation/list-tools-interrupted", interruptedException);
                }
            }

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject query = new JsonObject();
            query.addProperty("type", "string");
            properties.add("query", query);
            schema.add("properties", properties);
            return List.of(new McpSchemaMapper.McpRemoteTool(toolName, Optional.of("fixture-" + toolName),
                    schema, Optional.of(true)));
        }

        @Override
        public JsonObject callTool(String remoteToolName, String argumentsJson) {
            throw new AssertionError("task17/stale-generation/call-tool -> not expected during reload test");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
