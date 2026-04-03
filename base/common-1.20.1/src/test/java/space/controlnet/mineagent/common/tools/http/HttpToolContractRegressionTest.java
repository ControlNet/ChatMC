package space.controlnet.mineagent.common.tools.http;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.AgentTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolContractRegressionTest {
    @Test
    void task1_httpToolSpec_freezesArgsAndResultMetadata() {
        AgentTool tool = HttpToolMetadata.spec();

        assertEquals("http", tool.name());
        assertEquals("Fetch an HTTP or HTTPS resource and return a structured response envelope.", tool.description());
        assertEquals(
                "{url, method?, query?: [{name, value}], headers?: [{name, value}], bodyText?, bodyBase64?, timeoutMs?, followRedirects?, maxRedirects?, responseMode?}",
                tool.argsSchema()
        );
        assertEquals(List.of(
                "url: required absolute http or https URL",
                "method: optional HTTP method string, default GET",
                "query: optional ordered array of {name, value} entries appended to the URL; preserves duplicates",
                "headers: optional ordered array of {name, value} entries; preserves duplicates",
                "bodyText: optional UTF-8 request body text; mutually exclusive with bodyBase64; keep total args JSON under 65536 characters",
                "bodyBase64: optional base64 request body bytes for binary payloads; mutually exclusive with bodyText; keep total args JSON under 65536 characters",
                "timeoutMs: optional integer in range 1000..25000, default 10000",
                "followRedirects: optional boolean, default false",
                "maxRedirects: optional integer in range 0..10, default 5",
                "responseMode: optional string in auto|text|json|bytes, default auto"
        ), tool.argsDescription());
        assertEquals(
                "{kind, request: {url, method, query: [{name, value}], headers: [{name, value}], bodyText?, bodyBase64?, timeoutMs, followRedirects, maxRedirects, responseMode}, response?: {statusCode, finalUrl, redirectCount, headers: [{name, value}], contentType?, charset?, declaredContentLength?, bodyText?, bodyBase64?, bodyBytes}, failure?: {code, message}, truncated}",
                tool.resultSchema()
        );
        assertEquals(List.of(
                "kind: always \"http_result\"",
                "request: normalized request echo with resolved defaults, duplicate-preserving query/header entry arrays, and explicit redirect/response-mode settings",
                "response: populated for completed HTTP exchanges regardless of status code",
                "response.statusCode: integer HTTP status code",
                "response.finalUrl: final response URL after redirects",
                "response.redirectCount: number of redirects followed before the terminal response",
                "response.headers: ordered array of {name, value} entries; preserves duplicates",
                "response.contentType: optional normalized response media type string",
                "response.charset: optional normalized response charset name when declared",
                "response.declaredContentLength: optional declared response body length from Content-Length",
                "response.bodyText: decoded text response body when available; mutually exclusive with response.bodyBase64",
                "response.bodyBase64: base64 response body for binary or undecodable content; mutually exclusive with response.bodyText",
                "response.bodyBytes: total response body size in bytes",
                "failure: populated for local validation, timeout, transport, or runtime failures",
                "failure.code: stable local failure code string",
                "failure.message: stable local failure message string",
                "truncated: true when a body-bearing outcome was truncated"
        ), tool.resultDescription());
        assertEquals(List.of(
                "{\"tool\":\"http\",\"args\":{\"url\":\"https://example.com/api/search\",\"query\":[{\"name\":\"q\",\"value\":\"mineagent\"},{\"name\":\"tag\",\"value\":\"minecraft\"},{\"name\":\"tag\",\"value\":\"agent\"}],\"headers\":[{\"name\":\"Accept\",\"value\":\"application/json\"}]}}",
                "{\"tool\":\"http\",\"args\":{\"url\":\"https://example.com/api/messages\",\"method\":\"POST\",\"headers\":[{\"name\":\"Content-Type\",\"value\":\"application/json\"},{\"name\":\"Accept\",\"value\":\"application/json\"}],\"bodyText\":\"{\\\"message\\\":\\\"hello from MineAgent\\\"}\",\"timeoutMs\":25000,\"maxRedirects\":1}}"
        ), tool.examples());
    }

    @Test
    void task1_httpContracts_preserveEntryOrderingDefaultsAndKind() {
        HttpToolEntry firstDuplicate = new HttpToolEntry("tag", "minecraft");
        HttpToolEntry secondDuplicate = new HttpToolEntry("tag", "agent");
        HttpToolEntry acceptHeader = new HttpToolEntry("Accept", "application/json");
        HttpToolEntry cookieHeader = new HttpToolEntry("Cookie", "a=1");
        HttpToolEntry setCookieOne = new HttpToolEntry("Set-Cookie", "a=1");
        HttpToolEntry setCookieTwo = new HttpToolEntry("Set-Cookie", "b=2");

        HttpToolRequest request = new HttpToolRequest(
                "https://example.com/api/search",
                null,
                List.of(firstDuplicate, secondDuplicate),
                List.of(acceptHeader, cookieHeader),
                null,
                null,
                null,
                null,
                null,
                null
        );
        HttpToolResponse response = new HttpToolResponse(
                200,
                "https://example.com/api/search?tag=minecraft&tag=agent",
                2,
                List.of(setCookieOne, setCookieTwo),
                "application/json",
                "utf-8",
                11L,
                "{\"ok\":true}",
                null,
                11
        );
        HttpToolResultEnvelope result = new HttpToolResultEnvelope(request, response, false);

        assertEquals("GET", request.method());
        assertEquals(10_000, request.timeoutMs());
        assertEquals(false, request.followRedirects());
        assertEquals(5, request.maxRedirects());
        assertEquals("auto", request.responseMode());
        assertEquals(List.of(firstDuplicate, secondDuplicate), request.query());
        assertSame(firstDuplicate, request.query().get(0));
        assertSame(secondDuplicate, request.query().get(1));
        assertEquals(List.of(setCookieOne, setCookieTwo), response.headers());
        assertSame(setCookieOne, response.headers().get(0));
        assertSame(setCookieTwo, response.headers().get(1));
        assertEquals(2, response.redirectCount());
        assertEquals("utf-8", response.charset());
        assertEquals(11L, response.declaredContentLength());
        assertEquals("http_result", result.kind());
        assertSame(request, result.request());
        assertSame(response, result.response());
        assertEquals(null, result.failure());
        assertEquals(false, result.truncated());
    }

    @Test
    void task1_httpContracts_supportFailureEnvelopeAndTopLevelTruncation() {
        HttpToolRequest request = new HttpToolRequest(
                "https://example.com/failure",
                "POST",
                List.of(new HttpToolEntry("attempt", "1")),
                List.of(new HttpToolEntry("Accept", "application/json")),
                "payload",
                null,
                1_000,
                false,
                0,
                "json"
        );
        HttpToolFailure failure = new HttpToolFailure("invalid_args", "bodyText and bodyBase64 are mutually exclusive");

        HttpToolResultEnvelope result = new HttpToolResultEnvelope(request, failure, true);

        assertEquals("http_result", result.kind());
        assertSame(request, result.request());
        assertEquals(null, result.response());
        assertSame(failure, result.failure());
        assertEquals(true, result.truncated());
    }

    @Test
    void task1_httpContracts_rejectMutuallyExclusiveBodiesInvalidBoundsAndWrongKind() {
        IllegalArgumentException requestBodyConflict = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolRequest(
                        "https://example.com/upload",
                        "POST",
                        List.of(),
                        List.of(),
                        "text",
                        "dGV4dA==",
                        null,
                        null,
                        null,
                        null
                ));
        IllegalArgumentException responseBodyConflict = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(
                        200,
                        "https://example.com/upload",
                        0,
                        List.of(),
                        "application/octet-stream",
                        null,
                        null,
                        "text",
                        "dGV4dA==",
                        4
                ));
        IllegalArgumentException timeoutRange = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolRequest(
                        "https://example.com/slow",
                        "GET",
                        List.of(),
                        List.of(),
                        null,
                        null,
                        999,
                        false,
                        0,
                        "auto"
                ));
        IllegalArgumentException redirectRange = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolRequest(
                        "https://example.com/loop",
                        "GET",
                        List.of(),
                        List.of(),
                        null,
                        null,
                        1_000,
                        false,
                        11,
                        "auto"
                ));
        IllegalArgumentException responseModeRange = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolRequest(
                        "https://example.com/mode",
                        "GET",
                        List.of(),
                        List.of(),
                        null,
                        null,
                        1_000,
                        false,
                        0,
                        "yaml"
                ));
        IllegalArgumentException wrongKind = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResultEnvelope(
                        "wrong",
                        new HttpToolRequest("https://example.com", "GET", List.of(), List.of(), null, null, 1_000,
                                false, 0, "auto"),
                        new HttpToolResponse(200, "https://example.com", 0, List.of(), "text/plain", "utf-8", 2L,
                                "ok", null, 2),
                        null,
                        false
                ));
        IllegalArgumentException missingOutcome = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResultEnvelope(
                        HttpToolResultEnvelope.KIND,
                        new HttpToolRequest("https://example.com", "GET", List.of(), List.of(), null, null, 1_000,
                                false, 0, "auto"),
                        null,
                        null,
                        false
                ));
        IllegalArgumentException conflictingOutcome = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResultEnvelope(
                        HttpToolResultEnvelope.KIND,
                        new HttpToolRequest("https://example.com", "GET", List.of(), List.of(), null, null, 1_000,
                                false, 0, "auto"),
                        new HttpToolResponse(200, "https://example.com", 0, List.of(), "text/plain", "utf-8", 2L,
                                "ok", null, 2),
                        new HttpToolFailure("tool_timeout", "slow"),
                        false
                ));

        assertEquals("Request bodyText and bodyBase64 are mutually exclusive.", requestBodyConflict.getMessage());
        assertEquals("Response bodyText and bodyBase64 are mutually exclusive.", responseBodyConflict.getMessage());
        assertEquals("timeoutMs must be between 1000 and 25000.", timeoutRange.getMessage());
        assertEquals("maxRedirects must be between 0 and 10.", redirectRange.getMessage());
        assertEquals("responseMode must be one of auto, text, json, bytes.", responseModeRange.getMessage());
        assertEquals("kind must be http_result.", wrongKind.getMessage());
        assertEquals("response or failure is required.", missingOutcome.getMessage());
        assertEquals("response and failure are mutually exclusive.", conflictingOutcome.getMessage());
    }

    @Test
    void task1_httpContracts_returnImmutableCopies() {
        HttpToolRequest request = new HttpToolRequest(
                "https://example.com/immutable",
                "POST",
                List.of(new HttpToolEntry("a", "1")),
                List.of(new HttpToolEntry("X-Test", "yes")),
                "payload",
                null,
                1_000,
                false,
                0,
                "text"
        );
        HttpToolResponse response = new HttpToolResponse(
                201,
                "https://example.com/immutable",
                0,
                List.of(new HttpToolEntry("Location", "/immutable/1")),
                "text/plain",
                "utf-8",
                7L,
                "created",
                null,
                7
        );
        HttpToolResultEnvelope successEnvelope = new HttpToolResultEnvelope(request, response, false);
        HttpToolResultEnvelope failureEnvelope = new HttpToolResultEnvelope(
                request,
                new HttpToolFailure("tool_execution_failed", "boom"),
                true
        );

        assertThrows(UnsupportedOperationException.class, () -> request.query().add(new HttpToolEntry("b", "2")));
        assertThrows(UnsupportedOperationException.class, () -> request.headers().add(new HttpToolEntry("Y-Test", "no")));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().add(new HttpToolEntry("Retry-After", "1")));
        assertEquals(false, successEnvelope.truncated());
        assertEquals(true, failureEnvelope.truncated());
        assertTrue(HttpToolMetadata.SPEC == HttpToolMetadata.spec());
    }

    @Test
    void task1_httpContracts_validateEntryFailureAndResponseGuardrails() {
        IllegalArgumentException nullEntryName = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolEntry(null, "value"));
        IllegalArgumentException blankEntryName = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolEntry("   ", "value"));
        HttpToolEntry normalizedEntry = new HttpToolEntry("X-Test", null);

        IllegalArgumentException nullFailureCode = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolFailure(null, "boom"));
        IllegalArgumentException blankFailureCode = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolFailure("  ", "boom"));
        HttpToolFailure normalizedFailure = new HttpToolFailure("tool_execution_failed", null);

        IllegalArgumentException invalidStatus = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(99, "https://example.com", 0, List.of(), null, null, null,
                        null, null, 0));
        IllegalArgumentException blankFinalUrl = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(200, "   ", 0, List.of(), null, null, null,
                        null, null, 0));
        IllegalArgumentException negativeRedirectCount = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(200, "https://example.com", -1, List.of(), null, null, null,
                        null, null, 0));
        IllegalArgumentException negativeDeclaredContentLength = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(200, "https://example.com", 0, List.of(), null, null, -1L,
                        null, null, 0));
        IllegalArgumentException negativeBodyBytes = assertThrows(IllegalArgumentException.class,
                () -> new HttpToolResponse(200, "https://example.com", 0, List.of(), null, null, null,
                        null, null, -1));

        assertEquals("name is required.", nullEntryName.getMessage());
        assertEquals("name is required.", blankEntryName.getMessage());
        assertEquals("", normalizedEntry.value());

        assertEquals("code is required.", nullFailureCode.getMessage());
        assertEquals("code is required.", blankFailureCode.getMessage());
        assertEquals("", normalizedFailure.message());

        assertEquals("statusCode must be between 100 and 599.", invalidStatus.getMessage());
        assertEquals("finalUrl is required.", blankFinalUrl.getMessage());
        assertEquals("redirectCount must be greater than or equal to 0.", negativeRedirectCount.getMessage());
        assertEquals("declaredContentLength must be greater than or equal to 0.",
                negativeDeclaredContentLength.getMessage());
        assertEquals("bodyBytes must be greater than or equal to 0.", negativeBodyBytes.getMessage());
    }
}
