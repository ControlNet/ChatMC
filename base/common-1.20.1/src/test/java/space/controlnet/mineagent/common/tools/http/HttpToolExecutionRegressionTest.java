package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolExecutionRegressionTest {
    private static final HttpToolProvider PROVIDER = new HttpToolProvider();

    @Test
    void taskHttp_execution_http404_returnsSuccessfulToolResult() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-404")) {
            fixture.addFixedResponse("/missing", HttpToolLoopbackFixture.FixedResponse.text(
                    404,
                    "missing body",
                    StandardCharsets.UTF_8,
                    List.of(
                            new HttpToolEntry("X-Duplicate", "first"),
                            new HttpToolEntry("X-Duplicate", "second")
                    )
            ));

            ToolResult result = execute("""
                    {
                      "url":"%s",
                      "headers":[{"name":"Accept","value":"text/plain"}],
                      "responseMode":"text"
                    }
                    """.formatted(fixture.uri("/missing")));

            JsonObject payload = requirePayload("task5/http/404/payload", result);
            JsonObject response = payload.getAsJsonObject("response");

            assertTrue(result.success(), "task5/http/404/success");
            assertNull(result.error(), "task5/http/404/error");
            assertEquals("http_result", payload.get("kind").getAsString());
            assertEquals(404, response.get("statusCode").getAsInt());
            assertEquals(fixture.uri("/missing").toString(), response.get("finalUrl").getAsString());
            assertEquals(0, response.get("redirectCount").getAsInt());
            assertEquals("text/plain", response.get("contentType").getAsString());
            assertEquals("utf-8", response.get("charset").getAsString());
            assertEquals("missing body", response.get("bodyText").getAsString());
            assertEquals(12, response.get("bodyBytes").getAsInt());
            assertFalse(payload.get("truncated").getAsBoolean());

            JsonArray headers = response.getAsJsonArray("headers");
            assertEquals("content-length", headers.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("x-duplicate", headers.get(headers.size() - 2).getAsJsonObject().get("name").getAsString());
            assertEquals("first", headers.get(headers.size() - 2).getAsJsonObject().get("value").getAsString());
            assertEquals("second", headers.get(headers.size() - 1).getAsJsonObject().get("value").getAsString());
        }
    }

    @Test
    void taskHttp_execution_postBodyAndJsonMode_preserveNormalizedRequestAndResponseMetadata() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-post")) {
            fixture.addEchoResponse("/echo", HttpToolLoopbackFixture.EchoResponse.text(
                    201,
                    StandardCharsets.UTF_8,
                    List.of(new HttpToolEntry("Content-Type", "application/json; charset=utf-8"))
            ));

            String jsonBody = "{\"message\":\"hello\"}";
            ToolResult result = execute("""
                    {
                      "url":"%s",
                      "method":"post",
                      "query":[{"name":"tag","value":"one"},{"name":"tag","value":"two"}],
                      "headers":[{"name":"Content-Type","value":"application/json; charset=utf-8"}],
                      "bodyText":%s,
                      "responseMode":"json"
                    }
                    """.formatted(fixture.uri("/echo"), quoted(jsonBody)));

            JsonObject payload = requirePayload("task5/http/post/payload", result);
            JsonObject request = payload.getAsJsonObject("request");
            JsonObject response = payload.getAsJsonObject("response");

            assertTrue(result.success(), "task5/http/post/success");
            assertEquals("POST", request.get("method").getAsString());
            assertEquals(fixture.uri("/echo") + "?tag=one&tag=two", request.get("url").getAsString());
            assertEquals(jsonBody, request.get("bodyText").getAsString());
            assertEquals(201, response.get("statusCode").getAsInt());
            assertEquals("application/json", response.get("contentType").getAsString());
            assertEquals("utf-8", response.get("charset").getAsString());
            assertEquals(jsonBody, response.get("bodyText").getAsString());
            assertEquals(jsonBody.getBytes(StandardCharsets.UTF_8).length, response.get("bodyBytes").getAsInt());

            fixture.awaitRequestCount("/echo", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange exchange = fixture.lastRecordedExchange("/echo");
            assertEquals("POST", exchange.method());
            assertEquals("tag=one&tag=two", exchange.rawQuery());
            assertEquals(jsonBody, exchange.bodyText(StandardCharsets.UTF_8));
        }
    }

    @Test
    void taskHttp_execution_redirectsEnabledAndDisabled_areDeterministic() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-redirects")) {
            fixture.addRedirectChain(List.of(
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/start", 302, "/redirect/next"),
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/next", 307, "/redirect/final")
                    ),
                    "/redirect/final",
                    HttpToolLoopbackFixture.FixedResponse.text(
                            200,
                            "redirected",
                            StandardCharsets.UTF_8,
                            List.of(new HttpToolEntry("X-Final", "yes"))
                    )
            );

            ToolResult disabled = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/start") + "\","
                    + "\"followRedirects\":false"
                    + "}");
            JsonObject disabledPayload = requirePayload("task5/http/redirect-disabled/payload", disabled);
            JsonObject disabledResponse = disabledPayload.getAsJsonObject("response");
            assertTrue(disabled.success(), "task5/http/redirect-disabled/success");
            assertEquals(302, disabledResponse.get("statusCode").getAsInt());
            assertEquals(0, disabledResponse.get("redirectCount").getAsInt());
            assertEquals(fixture.uri("/redirect/start").toString(), disabledResponse.get("finalUrl").getAsString());

            ToolResult enabled = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/start") + "\","
                    + "\"followRedirects\":true,"
                    + "\"maxRedirects\":2"
                    + "}");
            JsonObject enabledPayload = requirePayload("task5/http/redirect-enabled/payload", enabled);
            JsonObject enabledResponse = enabledPayload.getAsJsonObject("response");
            assertTrue(enabled.success(), "task5/http/redirect-enabled/success");
            assertEquals(200, enabledResponse.get("statusCode").getAsInt());
            assertEquals(2, enabledResponse.get("redirectCount").getAsInt());
            assertEquals(fixture.uri("/redirect/final").toString(), enabledResponse.get("finalUrl").getAsString());
            assertEquals("redirected", enabledResponse.get("bodyText").getAsString());
        }
    }

    @Test
    void taskHttp_execution_timeoutAndRedirectOverflow_returnStableErrors() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-failures")) {
            fixture.addFixedResponse("/slow", HttpToolLoopbackFixture.FixedResponse.text(
                    200,
                    "slow",
                    StandardCharsets.UTF_8,
                    null
            ).withDelay(Duration.ofMillis(1_500)));
            fixture.addRedirectChain(List.of(
                            new HttpToolLoopbackFixture.RedirectStep("/loop/start", 302, "/loop/two"),
                            new HttpToolLoopbackFixture.RedirectStep("/loop/two", 302, "/loop/three"),
                            new HttpToolLoopbackFixture.RedirectStep("/loop/three", 302, "/loop/final")
                    ),
                    "/loop/final",
                    HttpToolLoopbackFixture.FixedResponse.text(200, "done", StandardCharsets.UTF_8, null)
            );

            ToolResult timeout = execute("{"
                    + "\"url\":\"" + fixture.uri("/slow") + "\","
                    + "\"timeoutMs\":1000"
                    + "}");
            JsonObject timeoutPayload = requirePayload("task5/http/timeout/payload", timeout);
            assertFalse(timeout.success(), "task5/http/timeout/success");
            assertEquals("tool_timeout", requireNonNull("task5/http/timeout/error", timeout.error()).code());
            assertEquals("HTTP request timed out after 1000 ms.", timeout.error().message());
            assertEquals("tool_timeout", timeoutPayload.getAsJsonObject("failure").get("code").getAsString());

            ToolResult redirectOverflow = execute("{"
                    + "\"url\":\"" + fixture.uri("/loop/start") + "\","
                    + "\"followRedirects\":true,"
                    + "\"maxRedirects\":1"
                    + "}");
            JsonObject redirectPayload = requirePayload("task5/http/redirect-overflow/payload", redirectOverflow);
            assertFalse(redirectOverflow.success(), "task5/http/redirect-overflow/success");
            assertEquals("too_many_redirects",
                    requireNonNull("task5/http/redirect-overflow/error", redirectOverflow.error()).code());
            assertEquals("HTTP redirect chain exceeded maxRedirects=1.", redirectOverflow.error().message());
            assertEquals("too_many_redirects",
                    redirectPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(redirectPayload.get("truncated").getAsBoolean());
        }
    }

    @Test
    void taskHttp_execution_connectionRefusalAndOversizedResponses_returnStructuredLocalFailures() throws Exception {
        int unusedPort = reserveUnusedPort();
        ToolResult connectionRefused = execute("{\"url\":\"http://127.0.0.1:" + unusedPort + "/refused\"}");
        JsonObject connectionPayload = requirePayload("task5/http/refused/payload", connectionRefused);
        assertFalse(connectionRefused.success(), "task5/http/refused/success");
        assertEquals("tool_execution_failed", requireNonNull("task5/http/refused/error", connectionRefused.error()).code());
        assertEquals("HTTP request failed because the connection was refused.", connectionRefused.error().message());
        assertEquals("tool_execution_failed", connectionPayload.getAsJsonObject("failure").get("code").getAsString());

        byte[] tooLargeBody = new byte[262_145];
        for (int index = 0; index < tooLargeBody.length; index++) {
            tooLargeBody[index] = 'x';
        }
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-too-large")) {
            fixture.addFixedResponse("/large", HttpToolLoopbackFixture.FixedResponse.binary(200, tooLargeBody, null));
            ToolResult tooLarge = execute("{\"url\":\"" + fixture.uri("/large") + "\"}");
            JsonObject tooLargePayload = requirePayload("task5/http/too-large/payload", tooLarge);
            assertFalse(tooLarge.success(), "task5/http/too-large/success");
            assertEquals("response_too_large", requireNonNull("task5/http/too-large/error", tooLarge.error()).code());
            assertEquals("HTTP response body exceeded 262144 bytes.", tooLarge.error().message());
            assertEquals(true, tooLargePayload.get("truncated").getAsBoolean());
        }
    }

    @Test
    void taskHttp_execution_textJsonAndBytesModes_handleBinaryAndForcedDecodeFailures() throws Exception {
        byte[] binaryBody = new byte[] {0x00, 0x01, (byte) 0xff, (byte) 0xfe};
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task5-modes")) {
            fixture.addFixedResponse("/binary", HttpToolLoopbackFixture.FixedResponse.binary(200, binaryBody, null));
            fixture.addFixedResponse("/json", new HttpToolLoopbackFixture.FixedResponse(
                    500,
                    List.of(new HttpToolEntry("Content-Type", "application/json; charset=utf-8")),
                    "{\"error\":true}".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));

            ToolResult autoBinary = execute("{\"url\":\"" + fixture.uri("/binary") + "\"}");
            JsonObject autoBinaryPayload = requirePayload("task5/http/auto-binary/payload", autoBinary);
            assertTrue(autoBinary.success(), "task5/http/auto-binary/success");
            assertEquals(Base64.getEncoder().encodeToString(binaryBody),
                    autoBinaryPayload.getAsJsonObject("response").get("bodyBase64").getAsString());

            ToolResult bytesBinary = execute("{"
                    + "\"url\":\"" + fixture.uri("/binary") + "\","
                    + "\"responseMode\":\"bytes\""
                    + "}");
            JsonObject bytesPayload = requirePayload("task5/http/bytes/payload", bytesBinary);
            assertTrue(bytesBinary.success(), "task5/http/bytes/success");
            assertEquals(Base64.getEncoder().encodeToString(binaryBody),
                    bytesPayload.getAsJsonObject("response").get("bodyBase64").getAsString());

            ToolResult forcedText = execute("{"
                    + "\"url\":\"" + fixture.uri("/binary") + "\","
                    + "\"responseMode\":\"text\""
                    + "}");
            JsonObject forcedTextPayload = requirePayload("task5/http/forced-text/payload", forcedText);
            assertFalse(forcedText.success(), "task5/http/forced-text/success");
            assertEquals("unsupported_response_body", requireNonNull("task5/http/forced-text/error", forcedText.error()).code());
            assertEquals("responseMode=text requires a decodable text response body.", forcedText.error().message());
            assertEquals("unsupported_response_body",
                    forcedTextPayload.getAsJsonObject("failure").get("code").getAsString());

            ToolResult forcedJson = execute("{"
                    + "\"url\":\"" + fixture.uri("/binary") + "\","
                    + "\"responseMode\":\"json\""
                    + "}");
            JsonObject forcedJsonPayload = requirePayload("task5/http/forced-json/payload", forcedJson);
            assertFalse(forcedJson.success(), "task5/http/forced-json/success");
            assertEquals("unsupported_response_body", requireNonNull("task5/http/forced-json/error", forcedJson.error()).code());
            assertEquals("responseMode=json requires a decodable text response body.", forcedJson.error().message());
            assertEquals("unsupported_response_body",
                    forcedJsonPayload.getAsJsonObject("failure").get("code").getAsString());

            ToolResult json500 = execute("{"
                    + "\"url\":\"" + fixture.uri("/json") + "\","
                    + "\"responseMode\":\"json\""
                    + "}");
            JsonObject json500Payload = requirePayload("task5/http/json500/payload", json500);
            assertTrue(json500.success(), "task5/http/json500/success");
            assertEquals(500, json500Payload.getAsJsonObject("response").get("statusCode").getAsInt());
            assertEquals("{\"error\":true}", json500Payload.getAsJsonObject("response").get("bodyText").getAsString());
        }
    }

    @Test
    void taskHttp_execution_redirectMethodRewriteBranches_areStable() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("taskf2-redirect-rewrite")) {
            fixture.addRedirect("/redirect/303-post", new HttpToolLoopbackFixture.RedirectResponse(
                    303,
                    "/echo/303-post",
                    null,
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/echo/303-post", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    null
            ));
            fixture.addRedirect("/redirect/303-head", new HttpToolLoopbackFixture.RedirectResponse(
                    303,
                    "/final/303-head",
                    null,
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/final/303-head", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "text/plain; charset=utf-8")),
                    "ignored-head-body".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addRedirect("/redirect/301-post", new HttpToolLoopbackFixture.RedirectResponse(
                    301,
                    "/echo/301-post",
                    null,
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/echo/301-post", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    null
            ));
            fixture.addRedirect("/redirect/302-post", new HttpToolLoopbackFixture.RedirectResponse(
                    302,
                    "/echo/302-post",
                    null,
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/echo/302-post", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    null
            ));
            fixture.addRedirect("/redirect/307-post", new HttpToolLoopbackFixture.RedirectResponse(
                    307,
                    "/echo/307-post",
                    null,
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/echo/307-post", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    null
            ));
            fixture.addRedirect("/redirect/308-post", new HttpToolLoopbackFixture.RedirectResponse(
                    308,
                    "/echo/308-post",
                    null,
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/echo/308-post", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    null
            ));

            ToolResult redirect303Post = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/303-post") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"bodyText\":\"rewrite-me\""
                    + "}");
            JsonObject redirect303PostPayload = requirePayload("taskf2/http/redirect-303-post/payload", redirect303Post);
            assertTrue(redirect303Post.success(), "taskf2/http/redirect-303-post/success");
            assertEquals(1, redirect303PostPayload.getAsJsonObject("response").get("redirectCount").getAsInt());
            fixture.awaitRequestCount("/echo/303-post", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect303PostExchange = fixture.lastRecordedExchange("/echo/303-post");
            assertEquals("GET", redirect303PostExchange.method());
            assertEquals(0, redirect303PostExchange.bodyBytes().length);
            assertFalse(redirect303PostPayload.getAsJsonObject("response").has("bodyText"),
                    "taskf2/http/redirect-303-post/body-text");

            ToolResult redirect303Head = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/303-head") + "\","
                    + "\"method\":\"HEAD\","
                    + "\"followRedirects\":true"
                    + "}");
            JsonObject redirect303HeadPayload = requirePayload("taskf2/http/redirect-303-head/payload", redirect303Head);
            assertTrue(redirect303Head.success(), "taskf2/http/redirect-303-head/success");
            fixture.awaitRequestCount("/final/303-head", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect303HeadExchange = fixture.lastRecordedExchange("/final/303-head");
            assertEquals("HEAD", redirect303HeadExchange.method());
            assertEquals(0, redirect303HeadPayload.getAsJsonObject("response").get("bodyBytes").getAsInt());
            assertFalse(redirect303HeadPayload.getAsJsonObject("response").has("bodyText"),
                    "taskf2/http/redirect-303-head/body-text");

            ToolResult redirect301Post = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/301-post") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"bodyText\":\"rewrite-301\""
                    + "}");
            assertTrue(redirect301Post.success(), "taskf2/http/redirect-301-post/success");
            fixture.awaitRequestCount("/echo/301-post", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect301PostExchange = fixture.lastRecordedExchange("/echo/301-post");
            assertEquals("GET", redirect301PostExchange.method());
            assertEquals(0, redirect301PostExchange.bodyBytes().length);

            ToolResult redirect302Post = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/302-post") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"bodyText\":\"rewrite-302\""
                    + "}");
            assertTrue(redirect302Post.success(), "taskf2/http/redirect-302-post/success");
            fixture.awaitRequestCount("/echo/302-post", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect302PostExchange = fixture.lastRecordedExchange("/echo/302-post");
            assertEquals("GET", redirect302PostExchange.method());
            assertEquals(0, redirect302PostExchange.bodyBytes().length);

            ToolResult redirect307Post = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/307-post") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"bodyText\":\"preserve-307\""
                    + "}");
            JsonObject redirect307Payload = requirePayload("taskf2/http/redirect-307-post/payload", redirect307Post);
            assertTrue(redirect307Post.success(), "taskf2/http/redirect-307-post/success");
            fixture.awaitRequestCount("/echo/307-post", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect307PostExchange = fixture.lastRecordedExchange("/echo/307-post");
            assertEquals("POST", redirect307PostExchange.method());
            assertEquals("preserve-307", redirect307PostExchange.bodyText(StandardCharsets.UTF_8));
            assertEquals("preserve-307", redirect307Payload.getAsJsonObject("response").get("bodyText").getAsString());

            ToolResult redirect308Post = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/308-post") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"bodyText\":\"preserve-308\""
                    + "}");
            JsonObject redirect308Payload = requirePayload("taskf2/http/redirect-308-post/payload", redirect308Post);
            assertTrue(redirect308Post.success(), "taskf2/http/redirect-308-post/success");
            fixture.awaitRequestCount("/echo/308-post", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirect308PostExchange = fixture.lastRecordedExchange("/echo/308-post");
            assertEquals("POST", redirect308PostExchange.method());
            assertEquals("preserve-308", redirect308PostExchange.bodyText(StandardCharsets.UTF_8));
            assertEquals("preserve-308", redirect308Payload.getAsJsonObject("response").get("bodyText").getAsString());
        }
    }

    @Test
    void taskHttp_execution_redirectLocationValidationFailures_areStable() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("taskf3-redirect-location")) {
            fixture.addRedirect("/redirect/ftp", new HttpToolLoopbackFixture.RedirectResponse(
                    302,
                    "ftp://example.com/archive",
                    null,
                    Duration.ZERO
            ));
            fixture.addRedirect("/redirect/no-host", new HttpToolLoopbackFixture.RedirectResponse(
                    302,
                    "http:///missing-host",
                    null,
                    Duration.ZERO
            ));
            fixture.addRedirect("/redirect/invalid", new HttpToolLoopbackFixture.RedirectResponse(
                    302,
                    "http://exa mple.com/bad",
                    null,
                    Duration.ZERO
            ));

            ToolResult ftpRedirect = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/ftp") + "\","
                    + "\"followRedirects\":true"
                    + "}");
            JsonObject ftpPayload = requirePayload("taskf3/http/redirect-ftp/payload", ftpRedirect);
            assertFalse(ftpRedirect.success(), "taskf3/http/redirect-ftp/success");
            assertEquals("tool_execution_failed", requireNonNull("taskf3/http/redirect-ftp/error", ftpRedirect.error()).code());
            assertEquals("HTTP redirect location must use http or https.", ftpRedirect.error().message());
            assertEquals("tool_execution_failed", ftpPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(ftpPayload.has("response"), "taskf3/http/redirect-ftp/response");
            assertFalse(ftpPayload.get("truncated").getAsBoolean());

            ToolResult noHostRedirect = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/no-host") + "\","
                    + "\"followRedirects\":true"
                    + "}");
            JsonObject noHostPayload = requirePayload("taskf3/http/redirect-no-host/payload", noHostRedirect);
            assertFalse(noHostRedirect.success(), "taskf3/http/redirect-no-host/success");
            assertEquals("tool_execution_failed",
                    requireNonNull("taskf3/http/redirect-no-host/error", noHostRedirect.error()).code());
            assertEquals("HTTP redirect location must resolve to an absolute http or https URL.",
                    noHostRedirect.error().message());
            assertEquals("tool_execution_failed", noHostPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(noHostPayload.has("response"), "taskf3/http/redirect-no-host/response");
            assertFalse(noHostPayload.get("truncated").getAsBoolean());

            ToolResult invalidRedirect = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/invalid") + "\","
                    + "\"followRedirects\":true"
                    + "}");
            JsonObject invalidPayload = requirePayload("taskf3/http/redirect-invalid/payload", invalidRedirect);
            assertFalse(invalidRedirect.success(), "taskf3/http/redirect-invalid/success");
            assertEquals("tool_execution_failed",
                    requireNonNull("taskf3/http/redirect-invalid/error", invalidRedirect.error()).code());
            assertEquals("HTTP redirect location is invalid.", invalidRedirect.error().message());
            assertEquals("tool_execution_failed", invalidPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(invalidPayload.has("response"), "taskf3/http/redirect-invalid/response");
            assertFalse(invalidPayload.get("truncated").getAsBoolean());
        }
    }

    @Test
    void taskHttp_execution_declaredContentLengthAndRedirectHeaderRebuild_areStable() throws Exception {
        String requestBody = "{\"hello\":true}";
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("taskf3-length-redirect-headers")) {
            fixture.addFixedResponse("/length", HttpToolLoopbackFixture.FixedResponse.text(
                    200,
                    "sized-body",
                    StandardCharsets.UTF_8,
                    List.of(new HttpToolEntry("X-Length-Test", "yes"))
            ));
            fixture.addRedirect("/redirect/preserve", new HttpToolLoopbackFixture.RedirectResponse(
                    307,
                    "/redirect/final",
                    List.of(new HttpToolEntry("X-Redirect", "start")),
                    Duration.ZERO
            ));
            fixture.addEchoResponse("/redirect/final", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    List.of(new HttpToolEntry("X-Final", "echo"))
            ));

            ToolResult sized = execute("{"
                    + "\"url\":\"" + fixture.uri("/length") + "\","
                    + "\"responseMode\":\"text\""
                    + "}");
            JsonObject sizedResponse = requirePayload("taskf3/http/declared-length/payload", sized)
                    .getAsJsonObject("response");
            assertTrue(sized.success(), "taskf3/http/declared-length/success");
            assertEquals("sized-body", sizedResponse.get("bodyText").getAsString());
            assertEquals(10, sizedResponse.get("bodyBytes").getAsInt());
            assertEquals(10L, sizedResponse.get("declaredContentLength").getAsLong());

            ToolResult redirected = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/preserve") + "\","
                    + "\"method\":\"POST\","
                    + "\"followRedirects\":true,"
                    + "\"headers\":["
                    + "{\"name\":\"Content-Type\",\"value\":\"application/json; charset=utf-8\"},"
                    + "{\"name\":\"X-Keep\",\"value\":\"redirected\"}"
                    + "],"
                    + "\"bodyText\":\"" + requestBody.replace("\"", "\\\"") + "\""
                    + "}");
            JsonObject redirectedPayload = requirePayload("taskf3/http/redirect-header-rebuild/payload", redirected);
            JsonObject redirectedResponse = redirectedPayload.getAsJsonObject("response");
            assertTrue(redirected.success(), "taskf3/http/redirect-header-rebuild/success");
            assertEquals(1, redirectedResponse.get("redirectCount").getAsInt());
            assertEquals(fixture.uri("/redirect/final").toString(), redirectedResponse.get("finalUrl").getAsString());
            assertEquals(requestBody, redirectedResponse.get("bodyText").getAsString());
            assertEquals((long) requestBody.getBytes(StandardCharsets.UTF_8).length,
                    redirectedResponse.get("declaredContentLength").getAsLong());

            fixture.awaitRequestCount("/redirect/final", 1, Duration.ofSeconds(2));
            HttpToolLoopbackFixture.RecordedExchange redirectedExchange = fixture.lastRecordedExchange("/redirect/final");
            assertEquals("POST", redirectedExchange.method());
            assertEquals(requestBody, redirectedExchange.bodyText(StandardCharsets.UTF_8));
            assertTrue(hasHeaderWithValue(redirectedExchange.headers(), "X-Keep", "redirected"),
                    "taskf3/http/redirect-header-rebuild/x-keep");
            assertTrue(hasHeaderWithValue(redirectedExchange.headers(), "Content-Type", "application/json; charset=utf-8"),
                    "taskf3/http/redirect-header-rebuild/content-type");
            assertTrue(hasHeaderWithValue(redirectedExchange.headers(), "Content-Length",
                    Integer.toString(requestBody.getBytes(StandardCharsets.UTF_8).length)),
                    "taskf3/http/redirect-header-rebuild/content-length");
        }
    }

    @Test
    void taskHttp_execution_noBodyCharsetAndStatelessEdges_areStable() throws Exception {
        byte[] isoBody = "caf\u00e9".getBytes(Charset.forName("ISO-8859-1"));
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task7-edge-matrix")) {
            fixture.addFixedResponse("/status/204", new HttpToolLoopbackFixture.FixedResponse(
                    204,
                    List.of(new HttpToolEntry("X-No-Content", "true")),
                    "ignored-204-body".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/status/304", new HttpToolLoopbackFixture.FixedResponse(
                    304,
                    List.of(new HttpToolEntry("ETag", "\"v1\"")),
                    "ignored-304-body".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/head", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "text/plain; charset=utf-8")),
                    "ignored-head-body".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/text/fallback", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "text/plain")),
                    "h\u00e9llo fallback".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/text/latin1", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "text/plain; charset=ISO-8859-1")),
                    isoBody,
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/stateless", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(
                            new HttpToolEntry("Set-Cookie", "session=first-call; Path=/"),
                            new HttpToolEntry("Content-Type", "text/plain; charset=utf-8")
                    ),
                    "stateless".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));

            ToolResult noContent = execute("{\"url\":\"" + fixture.uri("/status/204") + "\"}");
            JsonObject noContentResponse = requirePayload("task7/http/204/payload", noContent).getAsJsonObject("response");
            assertTrue(noContent.success(), "task7/http/204/success");
            assertEquals(204, noContentResponse.get("statusCode").getAsInt());
            assertEquals(0, noContentResponse.get("bodyBytes").getAsInt());
            assertFalse(noContentResponse.has("bodyText"), "task7/http/204/body-text");
            assertFalse(noContentResponse.has("bodyBase64"), "task7/http/204/body-base64");

            ToolResult notModified = execute("{\"url\":\"" + fixture.uri("/status/304") + "\"}");
            JsonObject notModifiedResponse = requirePayload("task7/http/304/payload", notModified).getAsJsonObject("response");
            assertTrue(notModified.success(), "task7/http/304/success");
            assertEquals(304, notModifiedResponse.get("statusCode").getAsInt());
            assertEquals(0, notModifiedResponse.get("bodyBytes").getAsInt());
            assertFalse(notModifiedResponse.has("bodyText"), "task7/http/304/body-text");
            assertFalse(notModifiedResponse.has("bodyBase64"), "task7/http/304/body-base64");

            ToolResult head = execute("{"
                    + "\"url\":\"" + fixture.uri("/head") + "\","
                    + "\"method\":\"HEAD\""
                    + "}");
            JsonObject headPayload = requirePayload("task7/http/head/payload", head);
            JsonObject headRequest = headPayload.getAsJsonObject("request");
            JsonObject headResponse = headPayload.getAsJsonObject("response");
            assertTrue(head.success(), "task7/http/head/success");
            assertEquals("HEAD", headRequest.get("method").getAsString());
            assertEquals(200, headResponse.get("statusCode").getAsInt());
            assertEquals(0, headResponse.get("bodyBytes").getAsInt());
            assertFalse(headResponse.has("bodyText"), "task7/http/head/body-text");
            assertFalse(headResponse.has("bodyBase64"), "task7/http/head/body-base64");
            fixture.awaitRequestCount("/head", 1, Duration.ofSeconds(2));
            assertEquals("HEAD", fixture.lastRecordedExchange("/head").method());

            ToolResult fallbackText = execute("{"
                    + "\"url\":\"" + fixture.uri("/text/fallback") + "\","
                    + "\"responseMode\":\"text\""
                    + "}");
            JsonObject fallbackResponse = requirePayload("task7/http/fallback/payload", fallbackText)
                    .getAsJsonObject("response");
            assertTrue(fallbackText.success(), "task7/http/fallback/success");
            assertEquals("text/plain", fallbackResponse.get("contentType").getAsString());
            assertFalse(fallbackResponse.has("charset"), "task7/http/fallback/charset");
            assertEquals("h\u00e9llo fallback", fallbackResponse.get("bodyText").getAsString());

            ToolResult isoText = execute("{"
                    + "\"url\":\"" + fixture.uri("/text/latin1") + "\","
                    + "\"responseMode\":\"text\""
                    + "}");
            JsonObject isoResponse = requirePayload("task7/http/iso/payload", isoText).getAsJsonObject("response");
            assertTrue(isoText.success(), "task7/http/iso/success");
            assertEquals("iso-8859-1", isoResponse.get("charset").getAsString());
            assertEquals("caf\u00e9", isoResponse.get("bodyText").getAsString());

            ToolResult firstStateless = execute("{\"url\":\"" + fixture.uri("/stateless") + "\"}");
            ToolResult secondStateless = execute("{\"url\":\"" + fixture.uri("/stateless") + "\"}");
            assertTrue(firstStateless.success(), "task7/http/stateless/first-success");
            assertTrue(secondStateless.success(), "task7/http/stateless/second-success");
            fixture.awaitRequestCount("/stateless", 2, Duration.ofSeconds(2));
            List<HttpToolLoopbackFixture.RecordedExchange> statelessExchanges = fixture.recordedExchanges("/stateless");
            assertEquals(2, statelessExchanges.size());
            assertFalse(hasHeader(statelessExchanges.get(0).headers(), "Cookie"), "task7/http/stateless/first-cookie");
            assertFalse(hasHeader(statelessExchanges.get(1).headers(), "Cookie"), "task7/http/stateless/second-cookie");
        }
    }

    @Test
    void taskHttp_execution_invalidJsonAndUnsupportedCharsetBranches_areStable() throws Exception {
        byte[] asciiBody = "plain-ascii".getBytes(StandardCharsets.UTF_8);
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("taskf2-json-charset")) {
            fixture.addFixedResponse("/json-invalid", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "application/json; charset=utf-8")),
                    "{bad-json".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/charset-unsupported-auto", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "text/plain; charset=not-a-real-charset")),
                    asciiBody,
                    Duration.ZERO
            ));
            fixture.addFixedResponse("/charset-unsupported-json", new HttpToolLoopbackFixture.FixedResponse(
                    200,
                    List.of(new HttpToolEntry("Content-Type", "application/json; charset=not-a-real-charset")),
                    "{}".getBytes(StandardCharsets.UTF_8),
                    Duration.ZERO
            ));

            ToolResult invalidJson = execute("{"
                    + "\"url\":\"" + fixture.uri("/json-invalid") + "\","
                    + "\"responseMode\":\"json\""
                    + "}");
            JsonObject invalidJsonPayload = requirePayload("taskf2/http/json-invalid/payload", invalidJson);
            assertFalse(invalidJson.success(), "taskf2/http/json-invalid/success");
            assertEquals("unsupported_response_body", requireNonNull("taskf2/http/json-invalid/error", invalidJson.error()).code());
            assertEquals("responseMode=json requires a valid JSON response body.", invalidJson.error().message());
            assertEquals("unsupported_response_body",
                    invalidJsonPayload.getAsJsonObject("failure").get("code").getAsString());

            ToolResult unsupportedCharsetAuto = execute("{\"url\":\"" + fixture.uri("/charset-unsupported-auto") + "\"}");
            JsonObject unsupportedCharsetAutoPayload = requirePayload("taskf2/http/charset-auto/payload", unsupportedCharsetAuto);
            assertTrue(unsupportedCharsetAuto.success(), "taskf2/http/charset-auto/success");
            assertEquals("not-a-real-charset",
                    unsupportedCharsetAutoPayload.getAsJsonObject("response").get("charset").getAsString());
            assertFalse(unsupportedCharsetAutoPayload.getAsJsonObject("response").has("bodyText"),
                    "taskf2/http/charset-auto/body-text");
            assertEquals(Base64.getEncoder().encodeToString(asciiBody),
                    unsupportedCharsetAutoPayload.getAsJsonObject("response").get("bodyBase64").getAsString());

            ToolResult unsupportedCharsetJson = execute("{"
                    + "\"url\":\"" + fixture.uri("/charset-unsupported-json") + "\","
                    + "\"responseMode\":\"json\""
                    + "}");
            JsonObject unsupportedCharsetJsonPayload = requirePayload("taskf2/http/charset-json/payload", unsupportedCharsetJson);
            assertFalse(unsupportedCharsetJson.success(), "taskf2/http/charset-json/success");
            assertEquals("unsupported_response_body",
                    requireNonNull("taskf2/http/charset-json/error", unsupportedCharsetJson.error()).code());
            assertEquals("responseMode=json requires a supported response charset.",
                    unsupportedCharsetJson.error().message());
            assertEquals("unsupported_response_body",
                    unsupportedCharsetJsonPayload.getAsJsonObject("failure").get("code").getAsString());
        }
    }

    @Test
    void taskHttp_execution_redirectDnsAndTruncationFailures_areStable() throws Exception {
        byte[] tooLargeBody = new byte[262_145];
        for (int index = 0; index < tooLargeBody.length; index++) {
            tooLargeBody[index] = 'x';
        }

        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task7-failure-matrix")) {
            fixture.addRedirectChain(List.of(
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/start", 302, "/redirect/next"),
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/next", 302, "/redirect/final")
                    ),
                    "/redirect/final",
                    HttpToolLoopbackFixture.FixedResponse.text(200, "redirected", StandardCharsets.UTF_8, null)
            );
            fixture.addFixedResponse("/large", HttpToolLoopbackFixture.FixedResponse.binary(200, tooLargeBody, null));

            ToolResult redirectDisabled = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/start") + "\","
                    + "\"followRedirects\":false"
                    + "}");
            JsonObject redirectDisabledPayload = requirePayload("task7/http/redirect-disabled/payload", redirectDisabled);
            JsonObject redirectDisabledResponse = redirectDisabledPayload.getAsJsonObject("response");
            assertTrue(redirectDisabled.success(), "task7/http/redirect-disabled/success");
            assertEquals(302, redirectDisabledResponse.get("statusCode").getAsInt());
            assertEquals(0, redirectDisabledResponse.get("redirectCount").getAsInt());
            assertEquals(fixture.uri("/redirect/start").toString(), redirectDisabledResponse.get("finalUrl").getAsString());

            ToolResult redirectOverflow = execute("{"
                    + "\"url\":\"" + fixture.uri("/redirect/start") + "\","
                    + "\"followRedirects\":true,"
                    + "\"maxRedirects\":1"
                    + "}");
            JsonObject redirectOverflowPayload = requirePayload("task7/http/redirect-overflow/payload", redirectOverflow);
            assertFalse(redirectOverflow.success(), "task7/http/redirect-overflow/success");
            assertEquals("too_many_redirects",
                    requireNonNull("task7/http/redirect-overflow/error", redirectOverflow.error()).code());
            assertEquals("HTTP redirect chain exceeded maxRedirects=1.", redirectOverflow.error().message());
            assertEquals("too_many_redirects",
                    redirectOverflowPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(redirectOverflowPayload.has("response"), "task7/http/redirect-overflow/response");
            assertFalse(redirectOverflowPayload.get("truncated").getAsBoolean());

            ToolResult dnsFailure = execute("{\"url\":\"http://"
                    + "task7-" + UUID.randomUUID() + ".invalid/unresolved\"}");
            JsonObject dnsPayload = requirePayload("task7/http/dns/payload", dnsFailure);
            assertFalse(dnsFailure.success(), "task7/http/dns/success");
            assertEquals("tool_execution_failed", requireNonNull("task7/http/dns/error", dnsFailure.error()).code());
            assertEquals("HTTP request failed because the host could not be resolved.", dnsFailure.error().message());
            assertEquals("tool_execution_failed", dnsPayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(dnsPayload.has("response"), "task7/http/dns/response");
            assertFalse(dnsPayload.get("truncated").getAsBoolean());

            ToolResult tooLarge = execute("{\"url\":\"" + fixture.uri("/large") + "\"}");
            JsonObject tooLargePayload = requirePayload("task7/http/too-large/payload", tooLarge);
            assertFalse(tooLarge.success(), "task7/http/too-large/success");
            assertEquals("response_too_large", requireNonNull("task7/http/too-large/error", tooLarge.error()).code());
            assertEquals("HTTP response body exceeded 262144 bytes.", tooLarge.error().message());
            assertEquals("response_too_large", tooLargePayload.getAsJsonObject("failure").get("code").getAsString());
            assertFalse(tooLargePayload.has("response"), "task7/http/too-large/response");
            assertTrue(tooLargePayload.get("truncated").getAsBoolean());
        }
    }

    private static ToolResult execute(String argsJson) {
        ToolOutcome outcome = PROVIDER.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, argsJson), true);
        return requireNonNull("task5/http/result", outcome.result());
    }

    private static JsonObject requirePayload(String assertionName, ToolResult result) {
        return requireJsonObject(assertionName, requireNonNull(assertionName + "/payload-json", result.payloadJson()));
    }

    private static JsonObject requireJsonObject(String assertionName, String json) {
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        assertNotNull(parsed, assertionName + "/json-object");
        return parsed;
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int reserveUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean hasHeader(List<HttpToolEntry> headers, String name) {
        for (HttpToolEntry header : headers) {
            if (header.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHeaderWithValue(List<HttpToolEntry> headers, String name, String value) {
        for (HttpToolEntry header : headers) {
            if (header.name().equalsIgnoreCase(name) && header.value().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        assertNotNull(value, assertionName);
        return value;
    }
}
