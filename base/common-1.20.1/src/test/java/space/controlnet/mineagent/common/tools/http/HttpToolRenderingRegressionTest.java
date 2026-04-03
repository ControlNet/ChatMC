package space.controlnet.mineagent.common.tools.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.client.render.HttpToolOutputRenderer;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.common.tools.ToolOutputFormatter;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolMessagePayload;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolPayload;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolRenderingRegressionTest {
    private static final HttpToolProvider PROVIDER = new HttpToolProvider();

    @AfterEach
    void clearRendererRegistry() {
        ToolOutputRendererRegistry.clear();
    }

    @Test
    void taskHttp_rendering_successRendersSummaryAndBodyPreview() throws Exception {
        registerRenderer();
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task6-success")) {
            fixture.addRedirectChain(
                    List.of(new HttpToolLoopbackFixture.RedirectStep("/start", 302, "/final")),
                    "/final",
                    HttpToolLoopbackFixture.FixedResponse.text(
                            200,
                            "hello\nredirect",
                            StandardCharsets.UTF_8,
                            List.of(new HttpToolEntry("Content-Type", "text/plain; charset=utf-8"))
                    )
            );

            String argsJson = "{"
                    + "\"url\":\"" + fixture.uri("/start") + "\","
                    + "\"followRedirects\":true,"
                    + "\"maxRedirects\":1,"
                    + "\"responseMode\":\"text\""
                    + "}";
            ToolResult result = execute(argsJson);
            String payloadJson = requireNonNull("task6/http/success/payload-json", result.payloadJson());

            assertTrue(result.success(), "task6/http/success/result-success");
            assertNull(result.error(), "task6/http/success/error");
            assertEquals(
                    List.of(
                            "HTTP GET " + fixture.uri("/final"),
                            "Status: 200",
                            "Content-Type: text/plain; charset=utf-8",
                            "Truncated: no",
                            "Body: hello\\nredirect"
                    ),
                    ToolOutputFormatter.formatLines(payloadJson),
                    "task6/http/success/rendered-lines"
            );

            String wrapped = ToolMessagePayload.wrap(new ToolCall(HttpToolRequest.TOOL_NAME, argsJson), result);
            ToolPayload parsed = requireNonNull("task6/http/success/parsed", ToolMessagePayload.parse(wrapped));
            assertContains("task6/http/success/wrapped-output", wrapped, "\"output\":{\"kind\":\"http_result\"");
            assertEquals(
                    requireJsonObject("task6/http/success/payload-expected", payloadJson),
                    requireJsonObject("task6/http/success/payload-actual", parsed.outputJson()),
                    "task6/http/success/parsed-output"
            );
            assertNull(parsed.error(), "task6/http/success/parsed-error");
        }
    }

    @Test
    void taskHttp_rendering_binaryAndBytesMode_renderByteCountsWithoutRawBodyDump() throws Exception {
        registerRenderer();
        byte[] binaryBody = new byte[] {0x00, 0x01, (byte) 0xff, (byte) 0xfe};
        String binaryBase64 = Base64.getEncoder().encodeToString(binaryBody);
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task6-binary")) {
            fixture.addFixedResponse(
                    "/binary",
                    HttpToolLoopbackFixture.FixedResponse.binary(
                            200,
                            binaryBody,
                            List.of(new HttpToolEntry("Content-Type", "application/octet-stream"))
                    )
            );
            fixture.addFixedResponse(
                    "/bytes-text",
                    HttpToolLoopbackFixture.FixedResponse.text(
                            200,
                            "byte mode text",
                            StandardCharsets.UTF_8,
                            List.of(new HttpToolEntry("Content-Type", "text/plain; charset=utf-8"))
                    )
            );

            List<String> autoBinaryLines = ToolOutputFormatter.formatLines(execute(
                    "{\"url\":\"" + fixture.uri("/binary") + "\"}"
            ).payloadJson());
            assertEquals(
                    List.of(
                            "HTTP GET " + fixture.uri("/binary"),
                            "Status: 200",
                            "Content-Type: application/octet-stream",
                            "Truncated: no",
                            "Body: 4 bytes (binary; preview omitted)"
                    ),
                    autoBinaryLines,
                    "task6/http/binary/auto-lines"
            );
            assertFalse(String.join("\n", autoBinaryLines).contains(binaryBase64), "task6/http/binary/base64-hidden");

            List<String> bytesModeLines = ToolOutputFormatter.formatLines(execute(
                    "{"
                            + "\"url\":\"" + fixture.uri("/bytes-text") + "\","
                            + "\"responseMode\":\"bytes\""
                            + "}"
            ).payloadJson());
            assertEquals(
                    List.of(
                            "HTTP GET " + fixture.uri("/bytes-text"),
                            "Status: 200",
                            "Content-Type: text/plain; charset=utf-8",
                            "Truncated: no",
                            "Body: 14 bytes (responseMode=bytes; preview omitted)"
                    ),
                    bytesModeLines,
                    "task6/http/binary/bytes-mode-lines"
            );
            assertFalse(String.join("\n", bytesModeLines).contains("byte mode text"), "task6/http/bytes/text-hidden");
        }
    }

    @Test
    void taskHttp_rendering_failurePayloadRoundTrips() throws Exception {
        registerRenderer();
        String argsJson = "{\"url\":\"http://127.0.0.1:" + reserveUnusedPort() + "/refused\"}";
        ToolResult result = execute(argsJson);
        String payloadJson = requireNonNull("task6/http/failure/payload-json", result.payloadJson());

        assertFalse(result.success(), "task6/http/failure/result-success");
        assertEquals(
                List.of(
                        "HTTP GET " + ToolOutputFormatter.parseJsonObject(argsJson).get("url").getAsString(),
                        "Failure: tool_execution_failed - HTTP request failed because the connection was refused.",
                        "Truncated: no"
                ),
                ToolOutputFormatter.formatLines(payloadJson),
                "task6/http/failure/rendered-lines"
        );

        String wrapped = ToolMessagePayload.wrap(new ToolCall(HttpToolRequest.TOOL_NAME, argsJson), result);
        ToolPayload parsed = requireNonNull("task6/http/failure/parsed", ToolMessagePayload.parse(wrapped));
        assertContains("task6/http/failure/wrapped-output", wrapped, "\"output\":{\"kind\":\"http_result\"");
        assertContains(
                "task6/http/failure/wrapped-structured-failure",
                wrapped,
                "\"failure\":{\"code\":\"tool_execution_failed\",\"message\":\"HTTP request failed because the connection was refused.\"}"
        );
        assertEquals(
                requireJsonObject("task6/http/failure/payload-expected", payloadJson),
                requireJsonObject("task6/http/failure/payload-actual", parsed.outputJson()),
                "task6/http/failure/parsed-output"
        );
        assertEquals("HTTP request failed because the connection was refused.", parsed.error(), "task6/http/failure/parsed-error");
    }

    private static void registerRenderer() {
        ToolOutputRendererRegistry.clear();
        ToolOutputRendererRegistry.register(new HttpToolOutputRenderer());
    }

    private static ToolResult execute(String argsJson) {
        ToolOutcome outcome = PROVIDER.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, argsJson), true);
        return requireNonNull("task6/http/result", outcome.result());
    }

    private static int reserveUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static com.google.gson.JsonObject requireJsonObject(String assertionName, String json) {
        return requireNonNull(assertionName, ToolOutputFormatter.parseJsonObject(json));
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        assertNotNull(value, assertionName);
        return value;
    }
}
