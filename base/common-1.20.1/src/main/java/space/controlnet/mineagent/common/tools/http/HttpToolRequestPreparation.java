package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class HttpToolRequestPreparation {
    private static final Pattern HTTP_METHOD_TOKEN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private final HttpToolRequest request;
    private final HttpRequest httpRequest;
    private final byte[] requestBodyBytes;

    private HttpToolRequestPreparation(HttpToolRequest request, HttpRequest httpRequest, byte[] requestBodyBytes) {
        this.request = Objects.requireNonNull(request, "request");
        this.httpRequest = Objects.requireNonNull(httpRequest, "httpRequest");
        this.requestBodyBytes = requestBodyBytes == null ? new byte[0] : requestBodyBytes.clone();
    }

    static HttpToolRequestPreparation prepare(JsonObject arguments) {
        String url = requireString(arguments, "url", "url is required.");
        String rawMethod = optionalString(arguments, "method");
        List<HttpToolEntry> query = readEntries(arguments, "query");
        List<HttpToolEntry> headers = readEntries(arguments, "headers");
        String bodyText = optionalString(arguments, "bodyText");
        String bodyBase64 = optionalString(arguments, "bodyBase64");
        Integer rawTimeoutMs = optionalInteger(arguments, "timeoutMs");
        Boolean rawFollowRedirects = optionalBoolean(arguments, "followRedirects");
        Integer rawMaxRedirects = optionalInteger(arguments, "maxRedirects");
        String rawResponseMode = optionalString(arguments, "responseMode");

        String method = HttpToolRequest.normalizeMethod(rawMethod);
        int timeoutMs = HttpToolRequest.DEFAULT_TIMEOUT_MS;
        boolean followRedirects = HttpToolRequest.DEFAULT_FOLLOW_REDIRECTS;
        int maxRedirects = HttpToolRequest.DEFAULT_MAX_REDIRECTS;
        String responseMode = HttpToolRequest.DEFAULT_RESPONSE_MODE;

        if (rawMethod != null && rawMethod.isBlank()) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64, timeoutMs,
                    followRedirects, maxRedirects, responseMode), "method must not be blank.");
        }
        if (!HTTP_METHOD_TOKEN.matcher(method).matches()) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64, timeoutMs,
                    followRedirects, maxRedirects, responseMode), "method must be a valid HTTP token.");
        }

        try {
            timeoutMs = HttpToolRequest.normalizeTimeoutMs(rawTimeoutMs);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64,
                    HttpToolRequest.DEFAULT_TIMEOUT_MS, followRedirects, maxRedirects, responseMode),
                    illegalArgumentException.getMessage());
        }

        followRedirects = HttpToolRequest.normalizeFollowRedirects(rawFollowRedirects);

        try {
            maxRedirects = HttpToolRequest.normalizeMaxRedirects(rawMaxRedirects);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64,
                    timeoutMs, followRedirects, HttpToolRequest.DEFAULT_MAX_REDIRECTS, responseMode),
                    illegalArgumentException.getMessage());
        }

        try {
            responseMode = HttpToolRequest.normalizeResponseMode(rawResponseMode);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64,
                    timeoutMs, followRedirects, maxRedirects, HttpToolRequest.DEFAULT_RESPONSE_MODE),
                    illegalArgumentException.getMessage());
        }

        if (bodyText != null && bodyBase64 != null) {
            throw validation(summary(url, method, query, headers, null, null, timeoutMs,
                    followRedirects, maxRedirects, responseMode),
                    "Request bodyText and bodyBase64 are mutually exclusive.");
        }

        URI normalizedUri;
        String normalizedUrl;
        try {
            normalizedUri = normalizeUri(url, query);
            normalizedUrl = normalizedUri.toString();
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(summary(url, method, query, headers, bodyText, bodyBase64,
                    timeoutMs, followRedirects, maxRedirects, responseMode), illegalArgumentException.getMessage());
        }

        byte[] requestBodyBytes;
        try {
            requestBodyBytes = decodeBodyBytes(bodyText, bodyBase64);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(summary(normalizedUrl, method, query, headers, bodyText, bodyBase64,
                    timeoutMs, followRedirects, maxRedirects, responseMode), illegalArgumentException.getMessage());
        }

        if (requestBodyBytes.length > 0 || bodyText != null || bodyBase64 != null) {
            if ("GET".equals(method) || "HEAD".equals(method)) {
                throw validation(summary(normalizedUrl, method, query, headers, bodyText, bodyBase64,
                        timeoutMs, followRedirects, maxRedirects, responseMode),
                        method + " requests must not include a body.");
            }
        }

        HttpToolRequest request = summary(normalizedUrl, method, query, headers, bodyText, bodyBase64,
                timeoutMs, followRedirects, maxRedirects, responseMode);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(normalizedUri)
                    .timeout(Duration.ofMillis(timeoutMs));
            for (HttpToolEntry header : headers) {
                builder.header(header.name(), header.value());
            }
            builder.method(method, bodyPublisher(requestBodyBytes));
            return new HttpToolRequestPreparation(request, builder.build(), requestBodyBytes);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw validation(request, requireMessage(illegalArgumentException, "HTTP request arguments are invalid."));
        }
    }

    HttpToolRequest request() {
        return request;
    }

    HttpRequest httpRequest() {
        return httpRequest;
    }

    byte[] requestBodyBytes() {
        return requestBodyBytes.clone();
    }

    private static HttpRequest.BodyPublisher bodyPublisher(byte[] requestBodyBytes) {
        return requestBodyBytes.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(requestBodyBytes);
    }

    private static HttpToolRequest summary(
            String url,
            String method,
            List<HttpToolEntry> query,
            List<HttpToolEntry> headers,
            String bodyText,
            String bodyBase64,
            int timeoutMs,
            boolean followRedirects,
            int maxRedirects,
            String responseMode
    ) {
        return new HttpToolRequest(url, method, query, headers, bodyText, bodyBase64,
                timeoutMs, followRedirects, maxRedirects, responseMode);
    }

    private static URI normalizeUri(String url, List<HttpToolEntry> query) {
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException("url must be an absolute HTTP or HTTPS URL.");
        }

        if (!parsed.isAbsolute() || parsed.getHost() == null) {
            throw new IllegalArgumentException("url must be an absolute HTTP or HTTPS URL.");
        }

        String scheme = Optional.ofNullable(parsed.getScheme())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("url scheme must be http or https.");
        }

        StringBuilder normalizedUrl = new StringBuilder();
        normalizedUrl.append(scheme).append("://");
        normalizedUrl.append(parsed.getRawAuthority());
        if (parsed.getRawPath() != null) {
            normalizedUrl.append(parsed.getRawPath());
        }

        String queryString = buildQueryString(parsed.getRawQuery(), query);
        if (queryString != null) {
            normalizedUrl.append('?').append(queryString);
        }
        if (parsed.getRawFragment() != null) {
            normalizedUrl.append('#').append(parsed.getRawFragment());
        }
        return URI.create(normalizedUrl.toString());
    }

    private static String buildQueryString(String existingRawQuery, List<HttpToolEntry> query) {
        if ((existingRawQuery == null || existingRawQuery.isEmpty()) && query.isEmpty()) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        if (existingRawQuery != null && !existingRawQuery.isEmpty()) {
            parts.add(existingRawQuery);
        }
        for (HttpToolEntry entry : query) {
            parts.add(encodeQueryComponent(entry.name()) + "=" + encodeQueryComponent(entry.value()));
        }
        return String.join("&", parts);
    }

    private static String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] decodeBodyBytes(String bodyText, String bodyBase64) {
        byte[] requestBodyBytes;
        if (bodyText != null) {
            requestBodyBytes = bodyText.getBytes(StandardCharsets.UTF_8);
        } else if (bodyBase64 != null) {
            try {
                requestBodyBytes = Base64.getDecoder().decode(bodyBase64);
            } catch (IllegalArgumentException illegalArgumentException) {
                throw new IllegalArgumentException("bodyBase64 must be valid base64.");
            }
        } else {
            requestBodyBytes = new byte[0];
        }

        if (requestBodyBytes.length > HttpToolRequest.MAX_REQUEST_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "request body must be " + HttpToolRequest.MAX_REQUEST_BODY_BYTES + " bytes or fewer."
            );
        }
        return requestBodyBytes;
    }

    private static List<HttpToolEntry> readEntries(JsonObject arguments, String fieldName) {
        JsonElement field = arguments.get(fieldName);
        if (field == null || field.isJsonNull()) {
            return List.of();
        }
        if (!field.isJsonArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array.");
        }

        JsonArray array = field.getAsJsonArray();
        List<HttpToolEntry> entries = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            if (entry == null || !entry.isJsonObject()) {
                throw new IllegalArgumentException(fieldName + "[" + index + "] must be an object.");
            }

            JsonObject entryObject = entry.getAsJsonObject();
            try {
                entries.add(new HttpToolEntry(
                        requireString(entryObject, "name", fieldName + "[" + index + "].name is required."),
                        optionalString(entryObject, "value")
                ));
            } catch (IllegalArgumentException illegalArgumentException) {
                throw new IllegalArgumentException(requireMessage(illegalArgumentException,
                        fieldName + "[" + index + "] is invalid."));
            }
        }
        return List.copyOf(entries);
    }

    private static String requireString(JsonObject arguments, String fieldName, String missingMessage) {
        JsonElement field = arguments.get(fieldName);
        if (field == null || field.isJsonNull()) {
            throw new IllegalArgumentException(missingMessage);
        }
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(fieldName + " must be a string.");
        }

        String value = field.getAsString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(missingMessage);
        }
        return value;
    }

    private static String optionalString(JsonObject arguments, String fieldName) {
        JsonElement field = arguments.get(fieldName);
        if (field == null || field.isJsonNull()) {
            return null;
        }
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(fieldName + " must be a string.");
        }
        return field.getAsString();
    }

    private static Integer optionalInteger(JsonObject arguments, String fieldName) {
        JsonElement field = arguments.get(fieldName);
        if (field == null || field.isJsonNull()) {
            return null;
        }
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
        try {
            return field.getAsInt();
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
    }

    private static Boolean optionalBoolean(JsonObject arguments, String fieldName) {
        JsonElement field = arguments.get(fieldName);
        if (field == null || field.isJsonNull()) {
            return null;
        }
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(fieldName + " must be a boolean.");
        }
        return field.getAsBoolean();
    }

    private static ValidationException validation(HttpToolRequest request, String message) {
        return new ValidationException(request, message);
    }

    private static String requireMessage(Throwable throwable, String fallbackMessage) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallbackMessage;
        }
        return throwable.getMessage();
    }

    static final class ValidationException extends IllegalArgumentException {
        private final HttpToolRequest request;

        private ValidationException(HttpToolRequest request, String message) {
            super(message);
            this.request = Objects.requireNonNull(request, "request");
        }

        HttpToolRequest request() {
            return request;
        }
    }
}
