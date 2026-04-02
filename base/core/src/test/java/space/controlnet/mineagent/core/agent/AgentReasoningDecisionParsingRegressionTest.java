package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

public final class AgentReasoningDecisionParsingRegressionTest {
    @Test
    void task18_reasoningParse_singleToolJsonCodeFence_returnsToolCallDecision() {
        AgentReasoningService service = newService();

        Optional<AgentDecision> decision = invokeParseDecision(service, """
                ```json
                {
                  "thinking":"check recipes",
                  "tool":"mc.find_recipes",
                  "args":{"itemId":"minecraft:chest","limit":2}
                }
                ```
                """);

        assertTrue("task18/reasoning-parse/single-tool/present", decision.isPresent());
        AgentDecision value = decision.orElseThrow();
        assertTrue("task18/reasoning-parse/single-tool/action", value.isToolCall());
        assertEquals("task18/reasoning-parse/single-tool/thinking", Optional.of("check recipes"), value.thinking());
        assertEquals("task18/reasoning-parse/single-tool/count", 1, value.toolCalls().size());
        assertEquals("task18/reasoning-parse/single-tool/name", "mc.find_recipes", value.toolCalls().get(0).toolName());
        assertContains("task18/reasoning-parse/single-tool/args", value.toolCalls().get(0).argsJson(), "minecraft:chest");
    }

    @Test
    void task18_reasoningParse_batchToolCalls_withResponseMessage_returnsRespondDecision() {
        AgentReasoningService service = newService();

        Optional<AgentDecision> decision = invokeParseDecision(service, """
                [
                  {
                    "thinking":"use tool then answer",
                    "tool_calls":[
                      {"tool":"mc.find_usage","args":{"itemId":"minecraft:oak_planks","limit":1}},
                      {"tool":"response","args":{"message":"done"}}
                    ]
                  }
                ]
                """);

        assertTrue("task18/reasoning-parse/batch-response/present", decision.isPresent());
        AgentDecision value = decision.orElseThrow();
        assertTrue("task18/reasoning-parse/batch-response/respond", value.isRespond());
        assertEquals("task18/reasoning-parse/batch-response/message", Optional.of("done"), value.response());
        assertEquals("task18/reasoning-parse/batch-response/thinking", Optional.of("use tool then answer"), value.thinking());
    }

    @Test
    void task18_reasoningParse_toolsAliasAndPrimitiveResponse_areSupported() {
        AgentReasoningService service = newService();

        Optional<AgentDecision> decision = invokeParseDecision(service,
                "{\"tools\":[{\"tool\":\"response\",\"args\":\"plain response\"}]}");

        assertTrue("task18/reasoning-parse/tools-alias/present", decision.isPresent());
        AgentDecision value = decision.orElseThrow();
        assertTrue("task18/reasoning-parse/tools-alias/respond", value.isRespond());
        assertEquals("task18/reasoning-parse/tools-alias/message", Optional.of("plain response"), value.response());
    }

    @Test
    void task18_reasoningParse_toolCallLimit_truncatesToolCallsDeterministically() {
        AgentReasoningService service = newService();
        service.setMaxToolCalls(1);

        Optional<AgentDecision> decision = invokeParseDecision(service, """
                {
                  "thinking":"many tools",
                  "tool_calls":[
                    {"tool":"mc.find_recipes","args":{"itemId":"minecraft:chest"}},
                    {"tool":"mc.find_usage","args":{"itemId":"minecraft:oak_planks"}}
                  ]
                }
                """);

        assertTrue("task18/reasoning-parse/tool-limit/present", decision.isPresent());
        AgentDecision value = decision.orElseThrow();
        assertTrue("task18/reasoning-parse/tool-limit/action", value.isToolCall());
        assertEquals("task18/reasoning-parse/tool-limit/count", 1, value.toolCalls().size());
        assertEquals("task18/reasoning-parse/tool-limit/first-kept", "mc.find_recipes", value.toolCalls().get(0).toolName());
    }

    @Test
    void task18_reasoningParse_invalidAndBoundaryPayloads_mapToEmptyDecision() {
        AgentReasoningService service = newService();
        String oversize = "a".repeat(ToolCallArgsParseBoundary.MAX_ARGS_JSON_LENGTH + 1);

        Optional<AgentDecision> blank = invokeParseDecision(service, "   ");
        Optional<AgentDecision> missingArgs = invokeParseDecision(service, "{\"tool\":\"mc.find_usage\"}");
        Optional<AgentDecision> invalidResponse = invokeParseDecision(service, "{\"tool\":\"response\",\"args\":{}} ");
        Optional<AgentDecision> boundary = invokeParseDecision(service,
                "{\"tool\":\"mc.find_usage\",\"args\":\"" + oversize + "\"}");
        Optional<AgentDecision> garbage = invokeParseDecision(service, "not json at all");

        assertTrue("task18/reasoning-parse/blank-empty", blank.isEmpty());
        assertTrue("task18/reasoning-parse/missing-args-empty", missingArgs.isEmpty());
        assertTrue("task18/reasoning-parse/invalid-response-empty", invalidResponse.isEmpty());
        assertTrue("task18/reasoning-parse/boundary-empty", boundary.isEmpty());
        assertTrue("task18/reasoning-parse/garbage-empty", garbage.isEmpty());
    }

    @Test
    void task18_reasoningRetryable_networkAndMessageSignals_areClassifiedConsistently() {
        assertTrue("task18/reasoning-retryable/io", invokeIsRetryable(new java.io.IOException("network broken")));
        assertTrue("task18/reasoning-retryable/connect", invokeIsRetryable(new java.net.ConnectException("refused")));
        assertTrue("task18/reasoning-retryable/message", invokeIsRetryable(new RuntimeException("Too many requests")));
        assertTrue("task18/reasoning-retryable/nested",
                invokeIsRetryable(new RuntimeException("wrapper", new java.net.SocketTimeoutException("timeout"))));
        assertFalse("task18/reasoning-retryable/null", invokeIsRetryable(null));
        assertFalse("task18/reasoning-retryable/non-retryable", invokeIsRetryable(new RuntimeException("validation failed")));
    }

    private static AgentReasoningService newService() {
        return new AgentReasoningService(
                (message, throwable) -> {
                },
                new LlmRateLimiter(60_000L),
                null,
                0,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static Optional<AgentDecision> invokeParseDecision(AgentReasoningService service, String content) {
        try {
            Method method = AgentReasoningService.class.getDeclaredMethod("parseDecision", String.class);
            method.setAccessible(true);
            return (Optional<AgentDecision>) method.invoke(service, content);
        } catch (Exception exception) {
            throw new AssertionError("task18/reasoning-parse/invoke", exception);
        }
    }

    private static boolean invokeIsRetryable(Throwable error) {
        try {
            Method method = AgentReasoningService.class.getDeclaredMethod("isRetryable", Throwable.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, error);
        } catch (Exception exception) {
            throw new AssertionError("task18/reasoning-retryable/invoke", exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean condition) {
        if (!condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
