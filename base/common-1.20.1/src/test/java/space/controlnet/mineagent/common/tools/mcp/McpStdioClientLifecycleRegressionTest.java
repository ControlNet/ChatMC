package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import space.controlnet.mineagent.common.testing.TimeoutUtility;
import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class McpStdioClientLifecycleRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void task17_stdioInitialize_listAndCall_succeeds() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "success");
        TrackingExecutor ioExecutor = new TrackingExecutor("task17-stdio-success-io");

        try (McpStdioClientSession session = McpStdioClientSession.open(
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
            assertEquals(fixture.cwd().toString(), structuredContent.get("cwd").getAsString());
            assertEquals(fixture.token(), structuredContent.get("token").getAsString());
            assertEquals("fixture-arg", structuredContent.get("arg").getAsString());

            assertTrue(ioExecutor.observedThreadNames().stream().allMatch(name -> name.startsWith("task17-stdio-success-io-")));
            assertFalse(ioExecutor.observedThreadNames().contains(Thread.currentThread().getName()));
        }

        TimeoutUtility.await("task17/stdio-close-marker", Duration.ofSeconds(2),
                () -> Files.exists(fixture.closeMarker()));
    }

    @Test
    void task17_stdioRequestIdCorrelation_interleavedResponses_areDeterministic() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "reorder_responses");

        try (McpStdioClientSession session = McpStdioClientSession.open(
                "docs",
                fixture.config(),
                Duration.ofSeconds(2),
                4096
        )) {
            ExecutorService callers = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                Future<List<McpSchemaMapper.McpRemoteTool>> listFuture = callers.submit(new Callable<>() {
                    @Override
                    public List<McpSchemaMapper.McpRemoteTool> call() throws Exception {
                        return session.listTools();
                    }
                });
                Future<JsonObject> callFuture = callers.submit(new Callable<>() {
                    @Override
                    public JsonObject call() throws Exception {
                        return session.callTool("search", "{\"query\":\"deterministic\"}");
                    }
                });

                List<McpSchemaMapper.McpRemoteTool> tools = listFuture.get(2, TimeUnit.SECONDS);
                JsonObject result = callFuture.get(2, TimeUnit.SECONDS);

                assertEquals(List.of("search"), tools.stream().map(McpSchemaMapper.McpRemoteTool::name).toList());
                assertEquals("call-response", result.getAsJsonObject("structuredContent").get("kind").getAsString());
            } finally {
                callers.shutdownNow();
            }
        }
    }

    @Test
    void task17_stdioTimeout_mapsToToolTimeoutError() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "timeout_on_call");

        try (McpStdioClientSession session = McpStdioClientSession.open(
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
    void task17_stdioMalformedJson_mapsToToolExecutionFailed() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "malformed_list");

        try (McpStdioClientSession session = McpStdioClientSession.open(
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
    void task17_stdioOversizedResponse_mapsToToolExecutionFailed() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "oversize_list");

        try (McpStdioClientSession session = McpStdioClientSession.open(
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
    void task17_stdioDisconnectOnCall_failsOutstandingRequestPromptly() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "disconnect_on_call");

        try (McpStdioClientSession session = McpStdioClientSession.open(
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

        TimeoutUtility.await("task17/stdio-disconnect-close-marker", Duration.ofSeconds(2),
                () -> Files.exists(fixture.closeMarker()));
    }

    @Test
    void task17_stdioStartupFailure_mapsToToolExecutionFailed() {
        McpServerConfig config = McpServerConfig.stdio(
                "definitely-missing-mineagent-mcp-command",
                List.of(),
                Map.of(),
                Optional.empty()
        );

        McpTransportException exception = expectTransportFailure(() -> McpStdioClientSession.open("docs", config));

        assertEquals("tool_execution_failed", exception.failureCode());
        assertEquals("tool execution failed", exception.failureMessage());
    }

    @Test
    void task17_stdioClose_signalsCleanShutdown() throws Exception {
        Fixture fixture = Fixture.create(tempDir, "success");
        McpStdioClientSession session = McpStdioClientSession.open(
                "docs",
                fixture.config(),
                Duration.ofSeconds(2),
                4096
        );

        session.close();

        TimeoutUtility.await("task17/stdio-clean-close-marker", Duration.ofSeconds(2),
                () -> Files.exists(fixture.closeMarker()));
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

    private record Fixture(McpServerConfig config, Path cwd, Path closeMarker, String token) {
        private static final String SERVER_SCRIPT = """
                import json
                import os
                import sys
                import time

                MODE = os.environ.get("CHATMC_FIXTURE_MODE", "success")
                TOKEN = os.environ.get("CHATMC_FIXTURE_TOKEN", "")
                CLOSE_MARKER = os.environ.get("CHATMC_CLOSE_MARKER", "")
                initialized_seen = False
                list_request = None
                call_request = None

                def send(payload):
                    sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\\n")
                    sys.stdout.flush()

                def list_response(message):
                    return {
                        "jsonrpc": "2.0",
                        "id": message["id"],
                        "result": {
                            "tools": [
                                {
                                    "name": "search",
                                    "description": "Search MCP docs",
                                    "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                            "query": {
                                                "type": "string"
                                            }
                                        },
                                        "required": ["query"]
                                    },
                                    "annotations": {
                                        "readOnlyHint": True
                                    }
                                }
                            ]
                        }
                    }

                def call_response(message, kind):
                    return {
                        "jsonrpc": "2.0",
                        "id": message["id"],
                        "result": {
                            "content": [
                                {
                                    "type": "text",
                                    "text": "search result"
                                }
                            ],
                            "structuredContent": {
                                "initializedSeen": initialized_seen,
                                "cwd": os.getcwd(),
                                "token": TOKEN,
                                "arg": sys.argv[1] if len(sys.argv) > 1 else "",
                                "kind": kind
                            },
                            "isError": False
                        }
                    }

                try:
                    for raw_line in sys.stdin:
                        line = raw_line.rstrip("\\n")
                        if not line:
                            continue
                        message = json.loads(line)
                        method = message.get("method")

                        if method == "initialize":
                            send({
                                "jsonrpc": "2.0",
                                "id": message["id"],
                                "result": {
                                    "protocolVersion": message["params"]["protocolVersion"],
                                    "capabilities": {
                                        "tools": {}
                                    },
                                    "serverInfo": {
                                        "name": "fixture",
                                        "version": "1.0.0"
                                    }
                                }
                            })
                        elif method == "notifications/initialized":
                            initialized_seen = True
                        elif method == "tools/list":
                            if MODE == "malformed_list":
                                sys.stdout.write("not-json\\n")
                                sys.stdout.flush()
                            elif MODE == "oversize_list":
                                huge = "x" * 2048
                                send({
                                    "jsonrpc": "2.0",
                                    "id": message["id"],
                                    "result": {
                                        "tools": [
                                            {
                                                "name": "search",
                                                "description": huge,
                                                "inputSchema": {
                                                    "type": "object"
                                                }
                                            }
                                        ]
                                    }
                                })
                            elif MODE == "reorder_responses":
                                send({
                                    "jsonrpc": "2.0",
                                    "method": "notifications/tools/list_changed"
                                })
                                list_request = message
                                if call_request is not None:
                                    send(call_response(call_request, "call-response"))
                                    send(list_response(list_request))
                                    list_request = None
                                    call_request = None
                            else:
                                send(list_response(message))
                        elif method == "tools/call":
                            if MODE == "timeout_on_call":
                                time.sleep(1.5)
                            elif MODE == "disconnect_on_call":
                                break
                            elif MODE == "reorder_responses":
                                call_request = message
                                if list_request is not None:
                                    send(call_response(call_request, "call-response"))
                                    send(list_response(list_request))
                                    list_request = None
                                    call_request = None
                            else:
                                send(call_response(message, "call-response"))
                finally:
                    if CLOSE_MARKER:
                        with open(CLOSE_MARKER, "w", encoding="utf-8") as marker:
                            marker.write("closed")
                """;

        private static Fixture create(Path root, String mode) throws IOException {
            Path cwd = Files.createDirectories(root.resolve(mode + "-cwd"));
            Path script = root.resolve(mode + "-fixture-server.py");
            Files.writeString(script, SERVER_SCRIPT);
            Path closeMarker = root.resolve(mode + "-close.marker");
            String token = "token-" + mode;

            Map<String, String> env = Map.of(
                    "CHATMC_FIXTURE_MODE", mode,
                    "CHATMC_FIXTURE_TOKEN", token,
                    "CHATMC_CLOSE_MARKER", closeMarker.toString()
            );
            McpServerConfig config = McpServerConfig.stdio(
                    "python3",
                    List.of(script.toString(), "fixture-arg"),
                    env,
                    Optional.of(cwd.toString())
            );
            return new Fixture(config, cwd, closeMarker, token);
        }
    }

    private static final class TrackingExecutor extends ThreadPoolExecutor {
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
