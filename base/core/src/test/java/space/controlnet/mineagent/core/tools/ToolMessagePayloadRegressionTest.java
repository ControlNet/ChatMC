package space.controlnet.mineagent.core.tools;

import org.junit.jupiter.api.Test;

public final class ToolMessagePayloadRegressionTest {
    @Test
    void task18_toolMessagePayload_wrapNullInputs_returnsNull() {
        assertEquals("task18/tool-payload/wrap-null", null, ToolMessagePayload.wrap(null, null, "   "));
    }

    @Test
    void task18_toolMessagePayload_wrapFallbackAndParsePrimitiveFields_areDeterministic() {
        ToolCall call = new ToolCall("mc.find_usage", "not-json-args");
        ToolResult result = ToolResult.error("not-json-output", "invalid_item", "bad item");

        String wrapped = ToolMessagePayload.wrap(call, result, "reasoning");
        ToolPayload parsed = ToolMessagePayload.parse(wrapped);

        assertContains("task18/tool-payload/wrapped-args-as-string", wrapped, "\"args\":\"not-json-args\"");
        assertContains("task18/tool-payload/wrapped-output-as-string", wrapped, "\"output\":\"not-json-output\"");
        assertEquals("task18/tool-payload/parsed-tool", "mc.find_usage", requireNonNull("task18/tool-payload/parsed", parsed).tool());
        assertEquals("task18/tool-payload/parsed-thinking", "reasoning", parsed.thinking());
        assertEquals("task18/tool-payload/parsed-args", "not-json-args", parsed.argsJson());
        assertEquals("task18/tool-payload/parsed-output", "not-json-output", parsed.outputJson());
        assertEquals("task18/tool-payload/parsed-error", "bad item", parsed.error());
    }

    @Test
    void task18_toolMessagePayload_parseGuards_handleInvalidShapesAndJson() {
        ToolPayload primitives = ToolMessagePayload.parse("{\"tool\":\"mc.find_recipes\",\"args\":123,\"output\":true,\"error\":\"oops\"}");

        assertEquals("task18/tool-payload/parse-empty-object", null, ToolMessagePayload.parse("{}"));
        assertEquals("task18/tool-payload/parse-array", null, ToolMessagePayload.parse("[]"));
        assertEquals("task18/tool-payload/parse-invalid-json", null, ToolMessagePayload.parse("{bad-json"));
        assertEquals("task18/tool-payload/parse-null-input", null, ToolMessagePayload.parse(null));
        assertEquals("task18/tool-payload/parse-blank-input", null, ToolMessagePayload.parse("   "));
        assertEquals("task18/tool-payload/parse-primitives-args", "123", requireNonNull("task18/tool-payload/primitives", primitives).argsJson());
        assertEquals("task18/tool-payload/parse-primitives-output", "true", primitives.outputJson());
        assertEquals("task18/tool-payload/parse-primitives-error", "oops", primitives.error());
    }

    @Test
    void task18_toolMessagePayload_wrapThinkingOnly_roundTripsWithoutTool() {
        String wrapped = ToolMessagePayload.wrap(null, null, "just-thought");
        ToolPayload parsed = ToolMessagePayload.parse(wrapped);

        assertEquals("task18/tool-payload/thinking-only/wrapped", "{\"thinking\":\"just-thought\"}", wrapped);
        assertEquals("task18/tool-payload/thinking-only/tool", null, requireNonNull("task18/tool-payload/thinking-only/parsed", parsed).tool());
        assertEquals("task18/tool-payload/thinking-only/thinking", "just-thought", parsed.thinking());
        assertEquals("task18/tool-payload/thinking-only/args", null, parsed.argsJson());
        assertEquals("task18/tool-payload/thinking-only/output", null, parsed.outputJson());
        assertEquals("task18/tool-payload/thinking-only/error", null, parsed.error());
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> expected non-null");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
