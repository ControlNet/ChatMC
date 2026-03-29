package space.controlnet.chatmc.core.tools.mcp;

import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolMessagePayload;
import space.controlnet.chatmc.core.tools.ToolPayload;
import space.controlnet.chatmc.core.tools.ToolResult;

public final class ToolResultPayloadOnErrorRegressionTest {
    @Test
    void task17_errorResult_preservesPayloadJson() {
        String payloadJson = "{\"structuredContent\":{\"hint\":\"retry with a narrower query\"}}";

        ToolResult result = ToolResult.error(payloadJson, "mcp_call_failed", "Remote MCP call failed");

        assertFalse("task17/tool-result/error/not-success", result.success());
        assertEquals("task17/tool-result/error/payload", payloadJson, result.payloadJson());
        assertEquals("task17/tool-result/error/code", "mcp_call_failed", requireNonNull("task17/tool-result/error/object", result.error()).code());
        assertEquals("task17/tool-result/error/message", "Remote MCP call failed", result.error().message());

        String wrapped = ToolMessagePayload.wrap(
                new ToolCall("mcp.docs.search", "{\"query\":\"mcp\"}"),
                result,
                "Need the structured failure payload"
        );
        ToolPayload parsed = ToolMessagePayload.parse(wrapped);

        assertContains("task17/tool-result/wrapped-has-output", wrapped, "\"output\":{\"structuredContent\":{\"hint\":\"retry with a narrower query\"}}");
        assertContains("task17/tool-result/wrapped-has-error", wrapped, "\"error\":\"Remote MCP call failed\"");
        assertEquals("task17/tool-result/parsed-tool", "mcp.docs.search", requireNonNull("task17/tool-result/parsed", parsed).tool());
        assertEquals("task17/tool-result/parsed-thinking", "Need the structured failure payload", parsed.thinking());
        assertEquals("task17/tool-result/parsed-output", payloadJson, parsed.outputJson());
        assertEquals("task17/tool-result/parsed-error", "Remote MCP call failed", parsed.error());
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (!value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }
}
