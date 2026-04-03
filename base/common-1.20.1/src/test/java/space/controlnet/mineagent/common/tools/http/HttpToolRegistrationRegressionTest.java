package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolRegistrationRegressionTest {
    @Test
    void task2_mineAgentRegistration_registersBuiltInHttpToolWithCallingThreadAffinity() {
        assertContains("task2/http/mineagent-registration",
                readSource("src/main/java/space/controlnet/mineagent/common/MineAgent.java"),
                "ToolRegistry.register(\"http\", new HttpToolProvider())");

        ToolRegistry.registerOrReplace("http", new HttpToolProvider());
        try {
            AgentTool tool = ToolRegistry.getToolSpec(HttpToolRequest.TOOL_NAME);

            assertNotNull(tool);
            assertSame(HttpToolMetadata.spec(), tool);
            assertEquals(ToolProvider.ExecutionAffinity.CALLING_THREAD,
                    ToolRegistry.getExecutionAffinity(HttpToolRequest.TOOL_NAME));
        } finally {
            ToolRegistry.unregister("http");
        }
    }

    @Test
    void task2_httpProvider_exposesSingleToolAndStablePreExecutionFailures() throws Exception {
        HttpToolProvider provider = new HttpToolProvider();

        assertEquals(List.of(HttpToolMetadata.spec()), provider.specs());
        assertEquals(ToolProvider.ExecutionAffinity.CALLING_THREAD, provider.executionAffinity());

        assertError("task2/http/invalid-tool", provider.execute(Optional.empty(), null, true), "invalid_tool",
                "Missing tool");
        assertError("task2/http/unknown-tool",
                provider.execute(Optional.empty(), new ToolCall("other", "{}"), true),
                "unknown_tool",
                "Unknown tool: other");
        assertError("task2/http/invalid-args/malformed-json",
                provider.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, "{bad-json"), true),
                "invalid_args",
                "HTTP tool arguments must be valid JSON.");
        assertError("task2/http/invalid-args/non-object-json",
                provider.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, "\"query\""), true),
                "invalid_args",
                "HTTP tool arguments must be a JSON object.");
        assertError("task2/http/invalid-args/missing-url",
                provider.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, "{}"), true),
                "invalid_args",
                "url is required.");

        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task2-registration")) {
            fixture.addFixedResponse("/status", HttpToolLoopbackFixture.FixedResponse.text(
                    200,
                    "ok",
                    StandardCharsets.UTF_8,
                    List.of(new HttpToolEntry("X-Test", "registration"))
            ));

            ToolOutcome executed = provider.execute(Optional.empty(), new ToolCall(
                    HttpToolRequest.TOOL_NAME,
                    "{\"url\":\"" + fixture.uri("/status") + "\"}"
            ), true);
            ToolResult result = requireNonNull("task2/http/executed/result", executed.result());
            JsonObject payload = requirePayload("task2/http/executed/payload", result.payloadJson());

            assertTrue(result.success());
            assertEquals(null, result.error());
            assertEquals("http_result", payload.get("kind").getAsString());
            assertEquals(fixture.uri("/status").toString(), payload.getAsJsonObject("request").get("url").getAsString());
            assertEquals("GET", payload.getAsJsonObject("request").get("method").getAsString());
            assertEquals(false, payload.get("truncated").getAsBoolean());
            assertEquals(200, payload.getAsJsonObject("response").get("statusCode").getAsInt());
            assertEquals(0, payload.getAsJsonObject("response").get("redirectCount").getAsInt());
            assertEquals("ok", payload.getAsJsonObject("response").get("bodyText").getAsString());
            fixture.awaitRequestCount("/status", 1, Duration.ofSeconds(2));
        }
    }

    private static String readSource(String relativePath) {
        try {
            return Files.readString(Path.of(relativePath));
        } catch (IOException ioException) {
            throw new AssertionError("task2/http/read-source", ioException);
        }
    }

    private static void assertContains(String assertionName, String source, String expectedSnippet) {
        if (source.contains(expectedSnippet)) {
            return;
        }
        throw new AssertionError(assertionName + " -> missing snippet: " + expectedSnippet);
    }

    private static void assertError(String assertionName, ToolOutcome outcome, String expectedCode, String expectedMessage) {
        ToolResult result = requireNonNull(assertionName + "/result", outcome.result());
        assertEquals(false, result.success(), assertionName + "/success");
        assertEquals(expectedCode, requireNonNull(assertionName + "/error", result.error()).code(), assertionName + "/code");
        assertEquals(expectedMessage, result.error().message(), assertionName + "/message");
    }

    private static JsonObject requirePayload(String assertionName, String payloadJson) {
        Object parsed = JsonParser.parseString(requireNonNull(assertionName + "/payload-json", payloadJson));
        if (parsed instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        throw new AssertionError(assertionName + " -> payload must be a JSON object");
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }
}
