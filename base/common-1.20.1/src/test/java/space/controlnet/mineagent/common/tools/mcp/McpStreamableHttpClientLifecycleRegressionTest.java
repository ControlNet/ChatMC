package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class McpStreamableHttpClientLifecycleRegressionTest {
    @Test
    void task17_httpInitialize_listAndCall_publicEndpoint_succeeds() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("success");
             TrackingExecutor ioExecutor = new TrackingExecutor("task17-http-success-io");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofSeconds(2),
                     4096,
                     ioExecutor
             )) {
            List<McpSchemaMapper.McpRemoteTool> tools = session.listTools();
            JsonObject result = session.callTool("search", "{\"query\":\"mcp\"}");

            assertEquals(1, tools.size());
            assertEquals("search", tools.get(0).name());
            assertEquals(Optional.of("Search MCP docs"), tools.get(0).description());
            assertEquals(Optional.of(true), tools.get(0).readOnlyHint());

            assertFalse(result.get("isError").getAsBoolean());
            JsonObject structuredContent = result.getAsJsonObject("structuredContent");
            assertNotNull(structuredContent);
            assertTrue(structuredContent.get("initializedSeen").getAsBoolean());
            assertEquals(fixture.sessionId(), structuredContent.get("sessionHeader").getAsString());
            assertEquals(McpHttpClientSession.PROTOCOL_VERSION, structuredContent.get("protocolHeader").getAsString());
            assertFalse(structuredContent.get("authorizationPresent").getAsBoolean());
            assertFalse(structuredContent.get("cookiePresent").getAsBoolean());

            List<RequestSnapshot> snapshots = fixture.requestSnapshots();
            assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/call"),
                    snapshots.stream().map(RequestSnapshot::method).toList());

            RequestSnapshot initialize = snapshots.get(0);
            assertNull(initialize.sessionHeader());
            assertNull(initialize.protocolHeader());
            assertFalse(initialize.authorizationPresent());
            assertFalse(initialize.cookiePresent());

            for (RequestSnapshot snapshot : snapshots.subList(1, snapshots.size())) {
                assertEquals(fixture.sessionId(), snapshot.sessionHeader());
                assertEquals(McpHttpClientSession.PROTOCOL_VERSION, snapshot.protocolHeader());
                assertFalse(snapshot.authorizationPresent());
                assertFalse(snapshot.cookiePresent());
            }

            assertFalse(ioExecutor.observedThreadNames().isEmpty());
            assertTrue(ioExecutor.observedThreadNames().stream().allMatch(name ->
                    name.startsWith("task17-http-success-io-")));
            assertFalse(ioExecutor.observedThreadNames().contains(Thread.currentThread().getName()));
        }
    }

    @Test
    void task17_httpNon2xxStatus_mapsToToolExecutionFailed() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("not_found_on_list");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofSeconds(2),
                     4096
             )) {
            McpTransportException exception = expectTransportFailure(session::listTools);

            assertEquals("tool_execution_failed", exception.failureCode());
            assertEquals("tool execution failed", exception.failureMessage());
        }
    }

    @Test
    void task17_httpTimeout_mapsToToolTimeoutError() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("timeout_on_call");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofMillis(150),
                     4096
             )) {
            McpTransportException exception = expectTransportFailure(() ->
                    session.callTool("search", "{\"query\":\"slow\"}"));

            assertEquals("tool_timeout", exception.failureCode());
            assertEquals("tool execution timeout", exception.failureMessage());
        }
    }

    @Test
    void task17_httpMalformedJson_mapsToToolExecutionFailed() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("malformed_list");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofSeconds(2),
                     4096
             )) {
            McpTransportException exception = expectTransportFailure(session::listTools);

            assertEquals("tool_execution_failed", exception.failureCode());
            assertEquals("tool execution failed", exception.failureMessage());
        }
    }

    @Test
    void task17_httpOversizedBody_mapsToToolExecutionFailed() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("oversize_list");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofSeconds(2),
                     256
             )) {
            McpTransportException exception = expectTransportFailure(session::listTools);

            assertEquals("tool_execution_failed", exception.failureCode());
            assertEquals("tool execution failed", exception.failureMessage());
        }
    }

    @Test
    void task17_httpDisconnect_mapsToToolExecutionFailed() throws Exception {
        try (HttpFixture fixture = HttpFixture.create("disconnect_on_call");
             McpHttpClientSession session = McpHttpClientSession.open(
                     "docs",
                     fixture.config(),
                     Duration.ofSeconds(2),
                     4096
             )) {
            McpTransportException exception = expectTransportFailure(() ->
                    session.callTool("search", "{\"query\":\"disconnect\"}"));

            assertEquals("tool_execution_failed", exception.failureCode());
            assertEquals("tool execution failed", exception.failureMessage());
        }
    }

    @Test
    void task17_httpFailingServer_doesNotAffectHealthySessionIsolation() throws Exception {
        try (HttpFixture failingFixture = HttpFixture.create("timeout_on_call");
             HttpFixture healthyFixture = HttpFixture.create("success");
             McpHttpClientSession failingSession = McpHttpClientSession.open(
                     "docs-failing",
                     failingFixture.config(),
                     Duration.ofMillis(150),
                     4096
             );
             McpHttpClientSession healthySession = McpHttpClientSession.open(
                     "docs-healthy",
                     healthyFixture.config(),
                     Duration.ofSeconds(2),
                     4096
             )) {
            McpTransportException exception = expectTransportFailure(() ->
                    failingSession.callTool("search", "{\"query\":\"slow\"}"));
            assertEquals("tool_timeout", exception.failureCode());

            List<McpSchemaMapper.McpRemoteTool> healthyTools = healthySession.listTools();
            JsonObject healthyResult = healthySession.callTool("search", "{\"query\":\"healthy\"}");

            assertEquals(List.of("search"), healthyTools.stream().map(McpSchemaMapper.McpRemoteTool::name).toList());
            assertFalse(healthyResult.get("isError").getAsBoolean());
            assertEquals("search result", healthyResult.getAsJsonArray("content")
                    .get(0).getAsJsonObject().get("text").getAsString());

            assertNotEquals(failingFixture.sessionId(), healthyFixture.sessionId());
            assertTrue(healthyFixture.requestSnapshots().stream()
                    .filter(snapshot -> !"initialize".equals(snapshot.method()))
                    .allMatch(snapshot -> healthyFixture.sessionId().equals(snapshot.sessionHeader())));
            assertTrue(failingFixture.requestSnapshots().stream()
                    .filter(snapshot -> !"initialize".equals(snapshot.method()))
                    .allMatch(snapshot -> failingFixture.sessionId().equals(snapshot.sessionHeader())));
        }
    }

    private static McpTransportException expectTransportFailure(ThrowingSupplier<?> supplier) {
        try {
            supplier.get();
        } catch (McpTransportException transportException) {
            return transportException;
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof McpTransportException transportException) {
                return transportException;
            }
            throw new AssertionError("unexpected execution failure type", executionException);
        } catch (Exception exception) {
            throw new AssertionError("expected McpTransportException", exception);
        }
        throw new AssertionError("expected McpTransportException");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record RequestSnapshot(
            String method,
            String sessionHeader,
            String protocolHeader,
            boolean authorizationPresent,
            boolean cookiePresent
    ) {
    }

    private static final class HttpFixture implements AutoCloseable {
        private final String mode;
        private final String sessionId;
        private final HttpServer server;
        private final ExecutorService serverExecutor;
        private final AtomicBoolean initializedSeen = new AtomicBoolean();
        private final List<RequestSnapshot> requestSnapshots = new CopyOnWriteArrayList<>();

        private HttpFixture(String mode, String sessionId, HttpServer server, ExecutorService serverExecutor) {
            this.mode = mode;
            this.sessionId = sessionId;
            this.server = server;
            this.serverExecutor = serverExecutor;
        }

        private static HttpFixture create(String mode) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService serverExecutor = java.util.concurrent.Executors.newCachedThreadPool(
                    new NamedThreadFactory("task17-http-fixture-" + mode));
            String sessionId = "session-" + mode + "-" + UUID.randomUUID();
            HttpFixture fixture = new HttpFixture(mode, sessionId, server, serverExecutor);
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

        private String sessionId() {
            return sessionId;
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

                JsonObject message = parseRequest(exchange);
                String method = requireMethod(message);
                requestSnapshots.add(new RequestSnapshot(
                        method,
                        firstHeader(exchange, "MCP-Session-Id"),
                        firstHeader(exchange, "MCP-Protocol-Version"),
                        exchange.getRequestHeaders().containsKey("Authorization"),
                        exchange.getRequestHeaders().containsKey("Cookie")
                ));

                if ("initialize".equals(method)) {
                    sendJson(exchange, initializeResponse(message), true);
                    return;
                }
                if ("notifications/initialized".equals(method)) {
                    initializedSeen.set(true);
                    sendStatus(exchange, 202);
                    return;
                }
                if ("tools/list".equals(method)) {
                    handleToolsList(exchange, message);
                    return;
                }
                if ("tools/call".equals(method)) {
                    handleToolsCall(exchange, message);
                    return;
                }

                sendStatus(exchange, 404);
            } catch (Exception exception) {
                sendStatus(exchange, 500);
            } finally {
                exchange.close();
            }
        }

        private JsonObject parseRequest(HttpExchange exchange) {
            try {
                JsonElement element = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    throw new IllegalStateException("Expected JSON object request.");
                }
                return element.getAsJsonObject();
            } catch (IOException ioException) {
                throw new IllegalStateException("Failed to read request body.", ioException);
            }
        }

        private void handleToolsList(HttpExchange exchange, JsonObject message) throws IOException {
            switch (mode) {
                case "malformed_list" -> sendRawJson(exchange, "not-json");
                case "oversize_list" -> sendJson(exchange, oversizeListResponse(message), false);
                case "not_found_on_list" -> sendStatus(exchange, 404);
                default -> sendJson(exchange, listResponse(message), false);
            }
        }

        private void handleToolsCall(HttpExchange exchange, JsonObject message) throws Exception {
            switch (mode) {
                case "timeout_on_call" -> {
                    Thread.sleep(1500L);
                    sendJson(exchange, callResponse(exchange, message), false);
                }
                case "disconnect_on_call" -> sendTruncatedJson(exchange, message);
                default -> sendJson(exchange, callResponse(exchange, message), false);
            }
        }

        private JsonObject initializeResponse(JsonObject message) {
            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", message.getAsJsonObject("params").get("protocolVersion").getAsString());
            result.add("capabilities", new JsonObject());

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("name", "fixture");
            serverInfo.addProperty("version", "1.0.0");
            result.add("serverInfo", serverInfo);

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", message.get("id"));
            response.add("result", result);
            return response;
        }

        private JsonObject listResponse(JsonObject message) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", "search");
            tool.addProperty("description", "Search MCP docs");

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
            response.add("id", message.get("id"));
            response.add("result", result);
            return response;
        }

        private JsonObject oversizeListResponse(JsonObject message) {
            String hugeDescription = "x".repeat(2048);
            JsonObject response = listResponse(message);
            response.getAsJsonObject("result")
                    .getAsJsonArray("tools")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("description", hugeDescription);
            return response;
        }

        private JsonObject callResponse(HttpExchange exchange, JsonObject message) {
            JsonObject contentEntry = new JsonObject();
            contentEntry.addProperty("type", "text");
            contentEntry.addProperty("text", "search result");

            JsonObject structuredContent = new JsonObject();
            structuredContent.addProperty("initializedSeen", initializedSeen.get());
            structuredContent.addProperty("sessionHeader", firstHeader(exchange, "MCP-Session-Id"));
            structuredContent.addProperty("protocolHeader", firstHeader(exchange, "MCP-Protocol-Version"));
            structuredContent.addProperty("authorizationPresent", exchange.getRequestHeaders().containsKey("Authorization"));
            structuredContent.addProperty("cookiePresent", exchange.getRequestHeaders().containsKey("Cookie"));

            JsonObject result = new JsonObject();
            result.add("content", JsonParser.parseString("[]").getAsJsonArray());
            result.getAsJsonArray("content").add(contentEntry);
            result.add("structuredContent", structuredContent);
            result.addProperty("isError", false);

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", message.get("id"));
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

        private void sendRawJson(HttpExchange exchange, String body) throws IOException {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }

        private void sendTruncatedJson(HttpExchange exchange, JsonObject message) throws IOException {
            byte[] responseBytes = ("{\"jsonrpc\":\"2.0\",\"id\":" + message.get("id").getAsLong()
                    + ",\"result\":{").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length + 32L);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
                outputStream.flush();
            }
        }

        private void sendStatus(HttpExchange exchange, int statusCode) throws IOException {
            exchange.sendResponseHeaders(statusCode, -1L);
        }

        private static String requireMethod(JsonObject message) {
            JsonElement method = message.get("method");
            if (method != null && method.isJsonPrimitive() && method.getAsJsonPrimitive().isString()) {
                return method.getAsString();
            }
            throw new IllegalStateException("Missing method.");
        }

        private static String firstHeader(HttpExchange exchange, String headerName) {
            return exchange.getRequestHeaders().getFirst(headerName);
        }

        @Override
        public void close() {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    private static final class TrackingExecutor extends ThreadPoolExecutor implements AutoCloseable {
        private final List<String> observedThreadNames = new CopyOnWriteArrayList<>();

        private TrackingExecutor(String threadPrefix) {
            super(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(threadPrefix));
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable runnable) {
            observedThreadNames.add(thread.getName());
            super.beforeExecute(thread, runnable);
        }

        private List<String> observedThreadNames() {
            return new ArrayList<>(observedThreadNames);
        }

        @Override
        public void close() {
            shutdownNow();
        }
    }

    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String threadPrefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String threadPrefix) {
            this.threadPrefix = threadPrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, threadPrefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
