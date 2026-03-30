package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolOutputFormatter;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolMessagePayload;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolPayload;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.util.List;

public final class McpToolProviderInvocationRenderRegressionTest {
    @Test
    void task17_execute_success_invokesProjectedRemoteToolAndReturnsNormalizedEnvelope() {
        RecordingSession session = new RecordingSession(successCallResult());
        McpToolProvider provider = new McpToolProvider("docs", session, List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"mcp\",\"limit\":2}"
        ), true);

        assertEquals("task17/mcp/success/affinity", ToolProvider.ExecutionAffinity.CALLING_THREAD,
                provider.executionAffinity());
        assertNull("task17/mcp/success/proposal", outcome.proposal());

        ToolResult result = requireNonNull("task17/mcp/success/result", outcome.result());
        assertTrue("task17/mcp/success/result-success", result.success());
        assertNull("task17/mcp/success/error", result.error());
        assertEquals("task17/mcp/success/remote-tool", "search", session.lastRemoteToolName());
        assertEquals("task17/mcp/success/arguments-json", "{\"query\":\"mcp\",\"limit\":2}", session.lastArgumentsJson());

        JsonObject payload = requireJsonObject("task17/mcp/success/payload", result.payloadJson());
        assertEquals("task17/mcp/success/server-alias", "docs", payload.get("serverAlias").getAsString());
        assertEquals("task17/mcp/success/qualified-tool", "mcp.docs.search", payload.get("qualifiedTool").getAsString());
        assertEquals("task17/mcp/success/remote-tool-name", "search", payload.get("remoteTool").getAsString());
        assertFalse("task17/mcp/success/is-error", payload.get("isError").getAsBoolean());
        assertEquals("task17/mcp/success/text-content-size", 1, payload.getAsJsonArray("textContent").size());
        assertEquals("task17/mcp/success/text-content-value", "search result", payload.getAsJsonArray("textContent")
                .get(0).getAsString());
        assertEquals("task17/mcp/success/structured-count", 2, payload.getAsJsonObject("structuredContent").size());
        assertEquals("task17/mcp/success/unsupported-size", 1, payload.getAsJsonArray("unsupportedContentTypes").size());
        assertEquals("task17/mcp/success/unsupported-type", "image", payload.getAsJsonArray("unsupportedContentTypes")
                .get(0).getAsString());
    }

    @Test
    void task17_execute_failure_preservesStructuredPayloadAndMessageOnlyHistorySemantics() {
        RecordingSession session = new RecordingSession(errorCallResult());
        McpToolProvider provider = new McpToolProvider("docs", session, List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"too broad\"}"
        ), true);

        assertNull("task17/mcp/error/proposal", outcome.proposal());
        ToolResult result = requireNonNull("task17/mcp/error/result", outcome.result());
        assertFalse("task17/mcp/error/result-success", result.success());
        assertEquals("task17/mcp/error/code", "tool_execution_failed", requireNonNull(
                "task17/mcp/error/error-object",
                result.error()
        ).code());
        assertEquals("task17/mcp/error/message", "Remote tool says no", result.error().message());

        JsonObject payload = requireJsonObject("task17/mcp/error/payload", result.payloadJson());
        assertTrue("task17/mcp/error/is-error", payload.get("isError").getAsBoolean());
        assertEquals("task17/mcp/error/payload-hint", "retry with a narrower query",
                payload.getAsJsonObject("structuredContent").get("hint").getAsString());

        String wrapped = ToolMessagePayload.wrap(new ToolCall("mcp.docs.search", "{\"query\":\"too broad\"}"), result);
        ToolPayload parsed = ToolMessagePayload.parse(wrapped);
        assertContains("task17/mcp/error/wrapped-output", wrapped,
                "\"structuredContent\":{\"hint\":\"retry with a narrower query\"}");
        assertEquals("task17/mcp/error/parsed-output", result.payloadJson(), requireNonNull(
                "task17/mcp/error/parsed-payload",
                parsed
        ).outputJson());
        assertEquals("task17/mcp/error/parsed-error", "Remote tool says no", parsed.error());
    }

    @Test
    void task17_textContentResult_rendersSummaryAndLinesDeterministically() {
        AgentTool tool = projectedSearchTool().toolSpec();
        String outputJson = """
                {
                  "serverAlias":"docs",
                  "qualifiedTool":"mcp.docs.search",
                  "remoteTool":"search",
                  "isError":false,
                  "textContent":["line one\\nline two"],
                  "structuredContent":{"hint":"unused because text wins"},
                  "unsupportedContentTypes":["image"]
                }
                """;

        ToolPayload payload = new ToolPayload(
                "mcp.docs.search",
                null,
                "{\"query\":\"mcp protocol\",\"path\":\"/ignored\",\"page\":2}",
                outputJson,
                null
        );

        ToolRender render = tool.render(payload);

        assertEquals("task17/mcp/render/summary", "mcp.docs.search (query=mcp protocol)",
                requireNonNull("task17/mcp/render/render", render).summaryKey());
        assertEquals("task17/mcp/render/summary-args", List.of(), render.summaryArgs());
        assertEquals("task17/mcp/render/lines", List.of("line one", "line two"), render.lines());
        assertEquals("task17/mcp/render/formatter-lines", List.of("line one", "line two"),
                ToolOutputFormatter.formatLines(outputJson));
    }

    @Test
    void task17_transportFailure_neverCreatesProposalAndUsesNormalizedFailureContract() {
        RecordingSession session = new RecordingSession(McpTransportException.timeout(new IllegalStateException("slow")));
        McpToolProvider provider = new McpToolProvider("docs", session, List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"slow\"}"
        ), true);

        assertNull("task17/mcp/transport/proposal", outcome.proposal());
        ToolResult result = requireNonNull("task17/mcp/transport/result", outcome.result());
        assertFalse("task17/mcp/transport/result-success", result.success());
        assertNull("task17/mcp/transport/payload", result.payloadJson());
        assertEquals("task17/mcp/transport/code", "tool_timeout", requireNonNull(
                "task17/mcp/transport/error-object",
                result.error()
        ).code());
        assertEquals("task17/mcp/transport/message", "tool execution timeout", result.error().message());
    }

    @Test
    void task17_invalidArgs_nonObjectAndMalformedJsonReturnInvalidArgs() {
        McpToolProvider provider = new McpToolProvider("docs", new RecordingSession(successCallResult()),
                List.of(projectedSearchTool()));

        ToolOutcome nonObject = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "\"query\""
        ), true);
        ToolOutcome malformed = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{bad-json"
        ), true);

        assertError("task17/mcp/invalid-args/non-object", nonObject, "invalid_args",
                "MCP tool arguments must be a JSON object.");
        assertError("task17/mcp/invalid-args/malformed", malformed, "invalid_args",
                "MCP tool arguments must be valid JSON.");
    }

    @Test
    void task17_runtimeException_returnsStableToolExecutionFailedContract() {
        McpToolProvider provider = new McpToolProvider("docs",
                new RuntimeFailingSession(new IllegalStateException("boom")),
                List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"boom\"}"
        ), true);

        assertError("task17/mcp/runtime-exception", outcome, "tool_execution_failed", "tool execution failed");
    }

    @Test
    void task17_remoteError_textMessageTakesPrecedenceOverStructuredFallback() {
        McpToolProvider provider = new McpToolProvider("docs",
                new RecordingSession(requireJsonObject("task17/mcp/error-precedence/fixture", """
                        {
                          "content":[{"type":"text","text":"Text wins"}],
                          "structuredContent":{"message":"Structured fallback"},
                          "isError":true
                        }
                        """)),
                List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"priority\"}"
        ), true);

        assertError("task17/mcp/error-precedence", outcome, "tool_execution_failed", "Text wins");
    }

    @Test
    void task17_remoteError_unsupportedContentOnlyUsesStableFallbackMessage() {
        McpToolProvider provider = new McpToolProvider("docs",
                new RecordingSession(requireJsonObject("task17/mcp/unsupported-only/fixture", """
                        {
                          "content":[{"type":"image","uri":"https://example.invalid/diagram.png"}],
                          "isError":true
                        }
                        """)),
                List.of(projectedSearchTool()));

        ToolOutcome outcome = provider.execute(java.util.Optional.empty(), new ToolCall(
                "mcp.docs.search",
                "{\"query\":\"image\"}"
        ), true);

        assertError("task17/mcp/unsupported-only", outcome, "tool_execution_failed",
                "MCP tool reported only unsupported content.");
        JsonObject payload = requireJsonObject("task17/mcp/unsupported-only/payload",
                requireNonNull("task17/mcp/unsupported-only/result", outcome.result()).payloadJson());
        assertEquals("task17/mcp/unsupported-only/type", "image",
                payload.getAsJsonArray("unsupportedContentTypes").get(0).getAsString());
    }

    private static McpSchemaMapper.McpProjectedTool projectedSearchTool() {
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        properties.add("query", query);
        inputSchema.add("properties", properties);
        return McpSchemaMapper.project("docs", new McpSchemaMapper.McpRemoteTool(
                "search",
                java.util.Optional.of("Search docs"),
                inputSchema,
                java.util.Optional.of(true)
        ));
    }

    private static JsonObject successCallResult() {
        return requireJsonObject("task17/mcp/success/fixture", """
                {
                  "content":[
                    {"type":"text","text":"search result"},
                    {"type":"image","uri":"https://example.invalid/image.png"}
                  ],
                  "structuredContent":{"total":1,"source":"fixture"},
                  "isError":false
                }
                """);
    }

    private static JsonObject errorCallResult() {
        return requireJsonObject("task17/mcp/error/fixture", """
                {
                  "content":[
                    {"type":"text","text":"Remote tool says no"}
                  ],
                  "structuredContent":{"hint":"retry with a narrower query"},
                  "isError":true
                }
                """);
    }

    private static JsonObject requireJsonObject(String assertionName, String rawJson) {
        try {
            JsonElement element = JsonParser.parseString(rawJson);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> invalid json", exception);
        }
        throw new AssertionError(assertionName + " -> expected json object");
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertError(String assertionName, ToolOutcome outcome, String expectedCode, String expectedMessage) {
        ToolResult result = requireNonNull(assertionName + "/result", requireNonNull(assertionName + "/outcome", outcome).result());
        assertFalse(assertionName + "/failure", result.success());
        assertEquals(assertionName + "/code", expectedCode, requireNonNull(assertionName + "/error", result.error()).code());
        assertEquals(assertionName + "/message", expectedMessage, result.error().message());
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (!value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertNull(String assertionName, Object value) {
        if (value == null) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected null but was: " + value);
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static final class RecordingSession implements McpClientSession {
        private final JsonObject result;
        private final McpTransportException failure;
        private String lastRemoteToolName;
        private String lastArgumentsJson;

        private RecordingSession(JsonObject result) {
            this.result = result;
            this.failure = null;
        }

        private RecordingSession(McpTransportException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public List<McpSchemaMapper.McpRemoteTool> listTools() {
            return List.of();
        }

        @Override
        public JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
            lastRemoteToolName = remoteToolName;
            lastArgumentsJson = argumentsJson;
            if (failure != null) {
                throw failure;
            }
            return result.deepCopy();
        }

        @Override
        public void close() {
        }

        private String lastRemoteToolName() {
            return lastRemoteToolName;
        }

        private String lastArgumentsJson() {
            return lastArgumentsJson;
        }
    }

    private static final class RuntimeFailingSession implements McpClientSession {
        private final RuntimeException failure;

        private RuntimeFailingSession(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<McpSchemaMapper.McpRemoteTool> listTools() {
            return List.of();
        }

        @Override
        public JsonObject callTool(String remoteToolName, String argumentsJson) {
            throw failure;
        }

        @Override
        public void close() {
        }
    }
}
