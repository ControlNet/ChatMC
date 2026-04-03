package space.controlnet.mineagent.common.tools.http;

import space.controlnet.mineagent.core.tools.AgentToolSpec;

import java.util.List;

public final class HttpToolMetadata {
    private static final int TOOL_ARGS_JSON_LIMIT_CHARS = 65_536;

    public static final String DESCRIPTION = "Fetch an HTTP or HTTPS resource and return a structured response envelope.";
    public static final String ARGS_SCHEMA = "{url, method?, query?: [{name, value}], headers?: [{name, value}], bodyText?, bodyBase64?, timeoutMs?, followRedirects?, maxRedirects?, responseMode?}";
    public static final List<String> ARGS_DESCRIPTION = List.of(
            "url: required absolute http or https URL",
            "method: optional HTTP method string, default GET",
            "query: optional ordered array of {name, value} entries appended to the URL; preserves duplicates",
            "headers: optional ordered array of {name, value} entries; preserves duplicates",
            "bodyText: optional UTF-8 request body text; mutually exclusive with bodyBase64; keep total args JSON under "
                    + TOOL_ARGS_JSON_LIMIT_CHARS + " characters",
            "bodyBase64: optional base64 request body bytes for binary payloads; mutually exclusive with bodyText; keep total args JSON under "
                    + TOOL_ARGS_JSON_LIMIT_CHARS + " characters",
            "timeoutMs: optional integer in range " + HttpToolRequest.MIN_TIMEOUT_MS + ".." + HttpToolRequest.MAX_TIMEOUT_MS
                    + ", default " + HttpToolRequest.DEFAULT_TIMEOUT_MS,
            "followRedirects: optional boolean, default " + HttpToolRequest.DEFAULT_FOLLOW_REDIRECTS,
            "maxRedirects: optional integer in range 0.." + HttpToolRequest.MAX_REDIRECTS
                    + ", default " + HttpToolRequest.DEFAULT_MAX_REDIRECTS,
            "responseMode: optional string in auto|text|json|bytes, default " + HttpToolRequest.DEFAULT_RESPONSE_MODE
    );
    public static final String RESULT_SCHEMA = "{kind, request: {url, method, query: [{name, value}], headers: [{name, value}], bodyText?, bodyBase64?, timeoutMs, followRedirects, maxRedirects, responseMode}, response?: {statusCode, finalUrl, redirectCount, headers: [{name, value}], contentType?, charset?, declaredContentLength?, bodyText?, bodyBase64?, bodyBytes}, failure?: {code, message}, truncated}";
    public static final List<String> RESULT_DESCRIPTION = List.of(
            "kind: always \"" + HttpToolResultEnvelope.KIND + "\"",
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
    );
    public static final List<String> EXAMPLES = List.of(
            "{\"tool\":\"http\",\"args\":{\"url\":\"https://example.com/api/search\",\"query\":[{\"name\":\"q\",\"value\":\"mineagent\"},{\"name\":\"tag\",\"value\":\"minecraft\"},{\"name\":\"tag\",\"value\":\"agent\"}],\"headers\":[{\"name\":\"Accept\",\"value\":\"application/json\"}]}}",
            "{\"tool\":\"http\",\"args\":{\"url\":\"https://example.com/api/messages\",\"method\":\"POST\",\"headers\":[{\"name\":\"Content-Type\",\"value\":\"application/json\"},{\"name\":\"Accept\",\"value\":\"application/json\"}],\"bodyText\":\"{\\\"message\\\":\\\"hello from MineAgent\\\"}\",\"timeoutMs\":25000,\"maxRedirects\":1}}"
    );
    public static final AgentToolSpec SPEC = AgentToolSpec.metadataOnly(
            HttpToolRequest.TOOL_NAME,
            DESCRIPTION,
            ARGS_SCHEMA,
            ARGS_DESCRIPTION,
            RESULT_SCHEMA,
            RESULT_DESCRIPTION,
            EXAMPLES
    );

    private HttpToolMetadata() {
    }

    public static AgentToolSpec spec() {
        return SPEC;
    }
}
