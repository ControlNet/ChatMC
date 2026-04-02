package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.ToolPayload;

import java.util.List;

public final class McpFallbackRendererRegressionTest {
    @Test
    void task18_mcpFallbackRenderer_summaryAndTextFallback_followPreferenceOrder() {
        ToolPayload payload = new ToolPayload(
                "mcp.docs.search",
                null,
                "{\"path\":\"/ignored\",\"query\":\"mcp protocol\"}",
                envelope("[\"line one\\nline two\"]", "{\"hint\":\"unused\"}", "[\"image\"]"),
                null
        );

        assertEquals("task18/mcp-fallback/summary", "mcp.docs.search (query=mcp protocol)",
                McpFallbackRenderer.render(payload).summaryKey());
        assertEquals("task18/mcp-fallback/text-lines", List.of("line one", "line two"),
                McpFallbackRenderer.renderLines(payload.outputJson()));
    }

    @Test
    void task18_mcpFallbackRenderer_structuredUnsupportedAndEmptyFallback_areDeterministic() {
        JsonObject structuredEnvelope = McpFallbackRenderer.parseJsonObject(envelope("[]", "{\"count\":2}", "[]"));
        JsonObject unsupportedEnvelope = McpFallbackRenderer.parseJsonObject(envelope("[]", "null", "[\"image\",\"audio\"]"));
        JsonObject emptyEnvelope = McpFallbackRenderer.parseJsonObject(envelope("[]", "null", "[]"));

        assertEquals(
                "task18/mcp-fallback/structured-lines",
                List.of("{", "  \"count\": 2", "}"),
                McpFallbackRenderer.renderLines(structuredEnvelope)
        );
        assertEquals(
                "task18/mcp-fallback/unsupported-lines",
                List.of("Unsupported MCP content type: image", "Unsupported MCP content type: audio"),
                McpFallbackRenderer.renderLines(unsupportedEnvelope)
        );
        assertEquals("task18/mcp-fallback/no-result", List.of("No result."), McpFallbackRenderer.renderLines(emptyEnvelope));
        assertEquals("task18/mcp-fallback/invalid-envelope", null, McpFallbackRenderer.renderLines("{\"status\":\"ok\"}"));
        assertEquals("task18/mcp-fallback/parse-invalid", null, McpFallbackRenderer.parseJsonObject("{bad-json"));
    }

    private static String envelope(String textContentJson, String structuredJson, String unsupportedJson) {
        return """
                {
                  "serverAlias":"docs",
                  "qualifiedTool":"mcp.docs.search",
                  "remoteTool":"search",
                  "isError":false,
                  "textContent":%s,
                  "structuredContent":%s,
                  "unsupportedContentTypes":%s
                }
                """.formatted(textContentJson, structuredJson, unsupportedJson);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
