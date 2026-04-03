package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolProviderValidationRegressionTest {
    private static final HttpToolProvider PROVIDER = new HttpToolProvider();

    @Test
    void taskHttp_validation_invalidUrlBlankMethodAndUnsupportedScheme_returnStructuredInvalidArgs() {
        ToolResult invalidUrl = execute("""
                {"url":"not a url","headers":[{"name":"Accept","value":"application/json"}]}
                """);
        assertInvalidArgs("task4/http/invalid-url", invalidUrl, "url must be an absolute HTTP or HTTPS URL.");
        JsonObject invalidUrlPayload = requirePayload("task4/http/invalid-url/payload", invalidUrl);
        assertEquals("not a url", invalidUrlPayload.getAsJsonObject("request").get("url").getAsString());
        assertEquals("GET", invalidUrlPayload.getAsJsonObject("request").get("method").getAsString());
        assertEquals(false, invalidUrlPayload.getAsJsonObject("request").get("followRedirects").getAsBoolean());

        ToolResult blankMethod = execute("""
                {"url":"https://example.com/search","method":"   ","query":[{"name":"tag","value":"mineagent"}]}
                """);
        assertInvalidArgs("task4/http/blank-method", blankMethod, "method must not be blank.");
        JsonObject blankMethodPayload = requirePayload("task4/http/blank-method/payload", blankMethod);
        assertEquals("GET", blankMethodPayload.getAsJsonObject("request").get("method").getAsString());
        assertEquals("https://example.com/search", blankMethodPayload.getAsJsonObject("request").get("url").getAsString());

        ToolResult unsupportedScheme = execute("""
                {"url":"ftp://example.com/archive","method":"post","headers":[{"name":"X-Test","value":"1"}]}
                """);
        assertInvalidArgs("task4/http/unsupported-scheme", unsupportedScheme, "url scheme must be http or https.");
        JsonObject unsupportedPayload = requirePayload("task4/http/unsupported-scheme/payload", unsupportedScheme);
        assertEquals("POST", unsupportedPayload.getAsJsonObject("request").get("method").getAsString());
        assertEquals("ftp://example.com/archive", unsupportedPayload.getAsJsonObject("request").get("url").getAsString());
        assertEquals(1, unsupportedPayload.getAsJsonObject("request").getAsJsonArray("headers").size());
    }

    @Test
    void taskHttp_validation_bodyTextAndBodyBase64Conflict_returnsInvalidArgs() {
        ToolResult result = execute("""
                {
                  "url":"https://example.com/upload",
                  "method":"post",
                  "query":[{"name":"tag","value":"alpha"},{"name":"tag","value":"beta"}],
                  "headers":[{"name":"Content-Type","value":"application/json"},{"name":"X-Test","value":"1"}],
                  "bodyText":"hello",
                  "bodyBase64":"AQID"
                }
                """);

        assertInvalidArgs("task4/http/body-conflict", result, "Request bodyText and bodyBase64 are mutually exclusive.");
        JsonObject payload = requirePayload("task4/http/body-conflict/payload", result);
        JsonObject request = payload.getAsJsonObject("request");

        assertEquals("POST", request.get("method").getAsString());
        assertEquals("https://example.com/upload", request.get("url").getAsString());
        assertEquals(2, request.getAsJsonArray("query").size());
        assertEquals("tag", request.getAsJsonArray("query").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("beta", request.getAsJsonArray("query").get(1).getAsJsonObject().get("value").getAsString());
        assertEquals(2, request.getAsJsonArray("headers").size());
        assertFalse(request.has("bodyText"));
        assertFalse(request.has("bodyBase64"));
    }

    @Test
    void taskHttp_validation_duplicateQueryPreservedAndInvalidBase64Rejected() {
        HttpToolRequestPreparation preparation = HttpToolRequestPreparation.prepare(requireJsonObject(
                "task4/http/duplicate-query/arguments",
                """
                        {
                          "url":"https://example.com/search?existing=yes",
                          "method":"post",
                          "query":[{"name":"a","value":"1"},{"name":"a","value":"2"},{"name":"space value","value":"hello world"}],
                          "headers":[{"name":"X-Test","value":"first"},{"name":"X-Test","value":"second"}],
                          "bodyBase64":"AQID",
                          "timeoutMs":1000,
                          "followRedirects":true,
                          "maxRedirects":7,
                          "responseMode":"JSON"
                        }
                        """
        ));

        assertEquals("POST", preparation.request().method());
        assertEquals("https://example.com/search?existing=yes&a=1&a=2&space%20value=hello%20world",
                preparation.request().url());
        assertEquals(List.of("first", "second"), preparation.httpRequest().headers().allValues("X-Test"));
        assertEquals(Duration.ofMillis(1000), preparation.httpRequest().timeout().orElseThrow());
        assertEquals("json", preparation.request().responseMode());
        assertEquals(true, preparation.request().followRedirects());
        assertEquals(7, preparation.request().maxRedirects());
        assertArrayEquals(new byte[]{1, 2, 3}, preparation.requestBodyBytes());
        assertArrayEquals(new byte[]{1, 2, 3}, readBodyPublisherBytes(preparation.httpRequest()));

        ToolResult invalidBase64 = execute("""
                {
                  "url":"https://example.com/search",
                  "query":[{"name":"a","value":"1"},{"name":"a","value":"2"}],
                  "bodyBase64":"%%%not-base64%%%"
                }
                """);
        assertInvalidArgs("task4/http/invalid-base64", invalidBase64, "bodyBase64 must be valid base64.");
        JsonObject invalidBase64Payload = requirePayload("task4/http/invalid-base64/payload", invalidBase64);
        assertEquals("https://example.com/search?a=1&a=2",
                invalidBase64Payload.getAsJsonObject("request").get("url").getAsString());
    }

    @Test
    void taskHttp_validation_invalidMethodTokenAndStructuralTypeMismatches_areDeterministic() {
        ToolResult invalidMethod = execute("""
                {
                  "url":"https://example.com/search",
                  "method":"po st",
                  "headers":[{"name":"Accept","value":"application/json"}]
                }
                """);
        assertInvalidArgs("task4/http/invalid-method-token", invalidMethod, "method must be a valid HTTP token.");
        JsonObject invalidMethodPayload = requirePayload("task4/http/invalid-method-token/payload", invalidMethod);
        assertEquals("PO ST", invalidMethodPayload.getAsJsonObject("request").get("method").getAsString());
        assertEquals("https://example.com/search", invalidMethodPayload.getAsJsonObject("request").get("url").getAsString());

        ToolResult queryNotArray = execute("""
                {
                  "url":"https://example.com/search",
                  "query":{"name":"tag","value":"mineagent"}
                }
                """);
        assertFalse(queryNotArray.success(), "task4/http/query-not-array/success");
        assertEquals("invalid_args", requireNonNull("task4/http/query-not-array/error", queryNotArray.error()).code());
        assertEquals("query must be an array.", queryNotArray.error().message());
        assertEquals(null, queryNotArray.payloadJson(), "task4/http/query-not-array/payload");

        ToolResult timeoutWrongType = execute("""
                {
                  "url":"https://example.com/search",
                  "timeoutMs":"fast"
                }
                """);
        assertFalse(timeoutWrongType.success(), "task4/http/timeout-wrong-type/success");
        assertEquals("invalid_args", requireNonNull("task4/http/timeout-wrong-type/error", timeoutWrongType.error()).code());
        assertEquals("timeoutMs must be an integer.", timeoutWrongType.error().message());
        assertEquals(null, timeoutWrongType.payloadJson(), "task4/http/timeout-wrong-type/payload");

        ToolResult followRedirectsWrongType = execute("""
                {
                  "url":"https://example.com/search",
                  "followRedirects":"yes"
                }
                """);
        assertFalse(followRedirectsWrongType.success(), "task4/http/follow-redirects-wrong-type/success");
        assertEquals("invalid_args",
                requireNonNull("task4/http/follow-redirects-wrong-type/error", followRedirectsWrongType.error()).code());
        assertEquals("followRedirects must be a boolean.", followRedirectsWrongType.error().message());
        assertEquals(null, followRedirectsWrongType.payloadJson(), "task4/http/follow-redirects-wrong-type/payload");
    }

    @Test
    void taskHttp_validation_builderLevelHeaderRejection_returnsStructuredInvalidArgs() {
        ToolResult invalidHeader = execute("""
                {
                  "url":"https://example.com/search",
                  "method":"post",
                  "headers":[
                    {"name":"Bad Header","value":"application/json"},
                    {"name":"X-Test","value":"kept"}
                  ],
                  "bodyText":"payload"
                }
                """);

        assertFalse(invalidHeader.success(), "task4/http/builder-invalid-header/success");
        assertEquals("invalid_args",
                requireNonNull("task4/http/builder-invalid-header/error", invalidHeader.error()).code());
        assertTrue(invalidHeader.error().message().toLowerCase().contains("header"),
                "task4/http/builder-invalid-header/message");

        JsonObject payload = requirePayload("task4/http/builder-invalid-header/payload", invalidHeader);
        JsonObject request = payload.getAsJsonObject("request");
        JsonObject failure = payload.getAsJsonObject("failure");

        assertEquals("http_result", payload.get("kind").getAsString());
        assertEquals(invalidHeader.error().message(), failure.get("message").getAsString());
        assertEquals("invalid_args", failure.get("code").getAsString());
        assertEquals("POST", request.get("method").getAsString());
        assertEquals("https://example.com/search", request.get("url").getAsString());
        assertEquals("payload", request.get("bodyText").getAsString());
        assertEquals(2, request.getAsJsonArray("headers").size());
        assertFalse(payload.get("truncated").getAsBoolean(), "task4/http/builder-invalid-header/truncated");
    }

    @Test
    void taskHttp_validation_getAndHeadBodiesAreRejectedDeterministically() {
        ToolResult getWithBody = execute("""
                {"url":"https://example.com/status","bodyText":"hello"}
                """);
        assertInvalidArgs("task4/http/get-body", getWithBody, "GET requests must not include a body.");
        JsonObject getPayload = requirePayload("task4/http/get-body/payload", getWithBody);
        assertEquals("hello", getPayload.getAsJsonObject("request").get("bodyText").getAsString());

        ToolResult headWithBody = execute("""
                {"url":"https://example.com/status","method":"head","bodyBase64":"AA=="}
                """);
        assertInvalidArgs("task4/http/head-body", headWithBody, "HEAD requests must not include a body.");
        JsonObject headPayload = requirePayload("task4/http/head-body/payload", headWithBody);
        assertEquals("HEAD", headPayload.getAsJsonObject("request").get("method").getAsString());
        assertEquals("AA==", headPayload.getAsJsonObject("request").get("bodyBase64").getAsString());
    }

    @Test
    void taskHttp_validation_timeoutRedirectNormalizationAndValidRequestsPrepareDeterministically() throws Exception {
        ToolResult invalidTimeout = execute("""
                {"url":"https://example.com/slow","timeoutMs":999}
                """);
        assertInvalidArgs("task4/http/invalid-timeout", invalidTimeout,
                "timeoutMs must be between 1000 and 25000.");
        JsonObject invalidTimeoutPayload = requirePayload("task4/http/invalid-timeout/payload", invalidTimeout);
        assertEquals(10000, invalidTimeoutPayload.getAsJsonObject("request").get("timeoutMs").getAsInt());

        ToolResult invalidRedirects = execute("""
                {"url":"https://example.com/loop","followRedirects":true,"maxRedirects":11}
                """);
        assertInvalidArgs("task4/http/invalid-redirects", invalidRedirects,
                "maxRedirects must be between 0 and 10.");
        JsonObject invalidRedirectsPayload = requirePayload("task4/http/invalid-redirects/payload", invalidRedirects);
        assertTrue(invalidRedirectsPayload.getAsJsonObject("request").get("followRedirects").getAsBoolean());
        assertEquals(5, invalidRedirectsPayload.getAsJsonObject("request").get("maxRedirects").getAsInt());

        HttpToolRequestPreparation valid = HttpToolRequestPreparation.prepare(requireJsonObject(
                "task4/http/valid/arguments",
                """
                        {
                          "url":"https://example.com/messages",
                          "method":"post",
                          "query":[{"name":"tag","value":"one"},{"name":"tag","value":"two"}],
                          "headers":[{"name":"X-Test","value":"first"},{"name":"X-Test","value":"second"}],
                          "bodyText":"héllo",
                          "responseMode":"text"
                        }
                        """
        ));
        assertEquals("POST", valid.request().method());
        assertEquals("https://example.com/messages?tag=one&tag=two", valid.request().url());
        assertEquals("héllo", valid.request().bodyText());
        assertEquals("text", valid.request().responseMode());
        assertEquals(List.of("first", "second"), valid.httpRequest().headers().allValues("X-Test"));
        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), valid.requestBodyBytes());
    }

    @Test
    void taskHttp_validation_requestBodySizeOverflow_returnsInvalidArgs() {
        String tooLargeBody = "x".repeat(HttpToolRequest.MAX_REQUEST_BODY_BYTES + 1);

        ToolResult result = execute("{"
                + "\"url\":\"https://example.com/upload\","
                + "\"method\":\"post\","
                + "\"bodyText\":\"" + tooLargeBody + "\""
                + "}");

        assertInvalidArgs("task4/http/body-overflow", result,
                "request body must be 32768 bytes or fewer.");
        JsonObject payload = requirePayload("task4/http/body-overflow/payload", result);
        assertEquals("POST", payload.getAsJsonObject("request").get("method").getAsString());
        assertEquals("https://example.com/upload", payload.getAsJsonObject("request").get("url").getAsString());
    }

    private static ToolResult execute(String argsJson) {
        ToolOutcome outcome = PROVIDER.execute(Optional.empty(), new ToolCall(HttpToolRequest.TOOL_NAME, argsJson), true);
        return requireNonNull("task4/http/result", outcome.result());
    }

    private static void assertInvalidArgs(String assertionName, ToolResult result, String expectedMessage) {
        assertFalse(result.success(), assertionName + "/success");
        assertEquals("invalid_args", requireNonNull(assertionName + "/error", result.error()).code(), assertionName + "/code");
        assertEquals(expectedMessage, result.error().message(), assertionName + "/message");
        JsonObject payload = requirePayload(assertionName + "/payload", result);
        assertEquals("http_result", payload.get("kind").getAsString(), assertionName + "/payload-kind");
        assertEquals(expectedMessage, payload.getAsJsonObject("failure").get("message").getAsString(),
                assertionName + "/payload-message");
        assertEquals("invalid_args", payload.getAsJsonObject("failure").get("code").getAsString(),
                assertionName + "/payload-code");
        assertFalse(payload.get("truncated").getAsBoolean(), assertionName + "/truncated");
    }

    private static JsonObject requireJsonObject(String assertionName, String json) {
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        assertNotNull(parsed, assertionName + "/json-object");
        return parsed;
    }

    private static JsonObject requirePayload(String assertionName, ToolResult result) {
        return requireJsonObject(assertionName, requireNonNull(assertionName + "/payload-json", result.payloadJson()));
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        assertNotNull(value, assertionName);
        return value;
    }

    private static byte[] readBodyPublisherBytes(HttpRequest request) {
        HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        bodyPublisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                outputStream.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });

        try {
            assertTrue(completed.await(5, TimeUnit.SECONDS), "task4/http/body-publisher/await-timeout");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("task4/http/body-publisher/interrupted", interruptedException);
        }

        Throwable publisherFailure = failure.get();
        if (publisherFailure != null) {
            throw new AssertionError("task4/http/body-publisher/failure", publisherFailure);
        }
        return outputStream.toByteArray();
    }
}
