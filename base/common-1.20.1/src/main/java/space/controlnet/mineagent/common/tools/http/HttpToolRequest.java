package space.controlnet.mineagent.common.tools.http;

import java.util.List;
import java.util.Locale;

public record HttpToolRequest(
        String url,
        String method,
        List<HttpToolEntry> query,
        List<HttpToolEntry> headers,
        String bodyText,
        String bodyBase64,
        Integer timeoutMs,
        Boolean followRedirects,
        Integer maxRedirects,
        String responseMode
) {
    public static final String TOOL_NAME = "http";
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    public static final int MIN_TIMEOUT_MS = 1_000;
    public static final int MAX_TIMEOUT_MS = 25_000;
    public static final int MAX_REQUEST_BODY_BYTES = 32_768;
    public static final boolean DEFAULT_FOLLOW_REDIRECTS = false;
    public static final int DEFAULT_MAX_REDIRECTS = 5;
    public static final int MAX_REDIRECTS = 10;
    public static final String DEFAULT_RESPONSE_MODE = "auto";

    public HttpToolRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required.");
        }
        method = normalizeMethod(method);
        query = query == null ? List.of() : List.copyOf(query);
        headers = headers == null ? List.of() : List.copyOf(headers);
        validateExclusiveBodyFields(bodyText, bodyBase64, "Request");
        timeoutMs = normalizeTimeoutMs(timeoutMs);
        followRedirects = normalizeFollowRedirects(followRedirects);
        maxRedirects = normalizeMaxRedirects(maxRedirects);
        responseMode = normalizeResponseMode(responseMode);
    }

    static void validateExclusiveBodyFields(String bodyText, String bodyBase64, String contractName) {
        if (bodyText != null && bodyBase64 != null) {
            throw new IllegalArgumentException(contractName + " bodyText and bodyBase64 are mutually exclusive.");
        }
    }

    static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    static int normalizeTimeoutMs(Integer timeoutMs) {
        int resolvedTimeoutMs = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
        if (resolvedTimeoutMs < MIN_TIMEOUT_MS || resolvedTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "timeoutMs must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS + "."
            );
        }
        return resolvedTimeoutMs;
    }

    static boolean normalizeFollowRedirects(Boolean followRedirects) {
        return followRedirects == null ? DEFAULT_FOLLOW_REDIRECTS : followRedirects;
    }

    static int normalizeMaxRedirects(Integer maxRedirects) {
        int resolvedMaxRedirects = maxRedirects == null ? DEFAULT_MAX_REDIRECTS : maxRedirects;
        if (resolvedMaxRedirects < 0 || resolvedMaxRedirects > MAX_REDIRECTS) {
            throw new IllegalArgumentException("maxRedirects must be between 0 and " + MAX_REDIRECTS + ".");
        }
        return resolvedMaxRedirects;
    }

    static String normalizeResponseMode(String responseMode) {
        String normalizedResponseMode = responseMode == null ? DEFAULT_RESPONSE_MODE : responseMode.trim().toLowerCase(Locale.ROOT);
        if (!"auto".equals(normalizedResponseMode)
                && !"text".equals(normalizedResponseMode)
                && !"json".equals(normalizedResponseMode)
                && !"bytes".equals(normalizedResponseMode)) {
            throw new IllegalArgumentException("responseMode must be one of auto, text, json, bytes.");
        }
        return normalizedResponseMode;
    }
}
