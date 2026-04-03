package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import space.controlnet.mineagent.core.tools.ToolResult;
import space.controlnet.mineagent.core.util.JsonSupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.channels.UnresolvedAddressException;

import static java.util.Map.Entry.comparingByKey;

final class HttpToolExecution {
    private static final int MAX_RESPONSE_BODY_BYTES = 262_144;

    private HttpToolExecution() {
    }

    static ToolResult execute(HttpToolRequestPreparation preparation) {
        HttpToolRequest request = preparation.request();
        try {
            ExecutedResponse executedResponse = executeRequest(preparation);
            String payloadJson = JsonSupport.GSON.toJson(new HttpToolResultEnvelope(
                    request,
                    executedResponse.response(),
                    executedResponse.truncated()
            ));
            return ToolResult.ok(payloadJson);
        } catch (HttpTimeoutException timeoutException) {
            return errorWithPayload(request, "tool_timeout",
                    "HTTP request timed out after " + request.timeoutMs() + " ms.", false);
        } catch (LocalFailureException localFailureException) {
            return errorWithPayload(request, localFailureException.code(), localFailureException.getMessage(),
                    localFailureException.truncated());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return errorWithPayload(request, "tool_execution_failed", "HTTP request execution was interrupted.",
                    false);
        } catch (IOException ioException) {
            return errorWithPayload(request, failureCode(ioException), failureMessage(ioException), false);
        } catch (RuntimeException runtimeException) {
            return errorWithPayload(request, failureCode(runtimeException), failureMessage(runtimeException), false);
        }
    }

    private static ExecutedResponse executeRequest(HttpToolRequestPreparation preparation)
            throws IOException, InterruptedException, LocalFailureException {
        HttpToolRequest request = preparation.request();
        HttpClient client = httpClient(Duration.ofMillis(request.timeoutMs()));
        RedirectedRequest redirectedRequest = new RedirectedRequest(preparation.httpRequest(), preparation.requestBodyBytes());
        int redirectCount = 0;

        while (true) {
            HttpResponse<InputStream> response = client.send(redirectedRequest.request(), HttpResponse.BodyHandlers.ofInputStream());
            Optional<String> redirectLocation = response.headers().firstValue("location");
            if (request.followRedirects() && isRedirectStatus(response.statusCode()) && redirectLocation.isPresent()) {
                try (InputStream ignored = response.body()) {
                    if (redirectCount >= request.maxRedirects()) {
                        throw new LocalFailureException(
                                "too_many_redirects",
                                "HTTP redirect chain exceeded maxRedirects=" + request.maxRedirects() + ".",
                                false
                        );
                    }
                }
                redirectedRequest = buildRedirectedRequest(redirectedRequest, request, response.statusCode(),
                        redirectLocation.orElseThrow());
                redirectCount++;
                continue;
            }

            try (InputStream inputStream = response.body()) {
                byte[] responseBytes = readResponseBodyBytes(inputStream);
                ResponseMetadata metadata = responseMetadata(response.headers());
                DecodedBody decodedBody = decodeBody(request.responseMode(), responseBytes, metadata);
                HttpToolResponse toolResponse = new HttpToolResponse(
                        response.statusCode(),
                        redirectedRequest.request().uri().toString(),
                        redirectCount,
                        flattenHeaders(response.headers()),
                        metadata.contentType(),
                        metadata.charset(),
                        metadata.declaredContentLength(),
                        decodedBody.bodyText(),
                        decodedBody.bodyBase64(),
                        responseBytes.length
                );
                return new ExecutedResponse(toolResponse, false);
            } catch (ResponseTooLargeException responseTooLargeException) {
                throw new LocalFailureException(
                        "response_too_large",
                        "HTTP response body exceeded " + MAX_RESPONSE_BODY_BYTES + " bytes.",
                        true
                );
            }
        }
    }

    private static HttpClient httpClient(Duration timeout) {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_NONE);
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static RedirectedRequest buildRedirectedRequest(RedirectedRequest currentRequest, HttpToolRequest request,
                                                            int statusCode, String location)
            throws LocalFailureException {
        URI redirectUri = resolveRedirectUri(currentRequest.request().uri(), location);
        RedirectBehavior redirectBehavior = redirectBehavior(currentRequest.request().method(), statusCode,
                currentRequest.bodyBytes());
        HttpRequest.Builder builder = HttpRequest.newBuilder(redirectUri)
                .timeout(Duration.ofMillis(request.timeoutMs()));
        for (HttpToolEntry header : request.headers()) {
            if (skipRedirectHeader(header.name())) {
                continue;
            }
            builder.header(header.name(), header.value());
        }
        builder.method(redirectBehavior.method(), bodyPublisher(redirectBehavior.bodyBytes()));
        return new RedirectedRequest(builder.build(), redirectBehavior.bodyBytes());
    }

    private static URI resolveRedirectUri(URI currentUri, String location) throws LocalFailureException {
        try {
            URI resolvedUri = currentUri.resolve(location).normalize();
            String scheme = Optional.ofNullable(resolvedUri.getScheme())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .orElse("");
            if (!resolvedUri.isAbsolute() || resolvedUri.getHost() == null) {
                throw new LocalFailureException(
                        "tool_execution_failed",
                        "HTTP redirect location must resolve to an absolute http or https URL.",
                        false
                );
            }
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new LocalFailureException(
                        "tool_execution_failed",
                        "HTTP redirect location must use http or https.",
                        false
                );
            }
            return resolvedUri;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new LocalFailureException(
                    "tool_execution_failed",
                    "HTTP redirect location is invalid.",
                    false
            );
        }
    }

    private static RedirectBehavior redirectBehavior(String method, int statusCode, byte[] bodyBytes) {
        if (statusCode == 303) {
            if ("HEAD".equals(method)) {
                return new RedirectBehavior("HEAD", new byte[0]);
            }
            return new RedirectBehavior("GET", new byte[0]);
        }
        if ((statusCode == 301 || statusCode == 302) && "POST".equals(method)) {
            return new RedirectBehavior("GET", new byte[0]);
        }
        return new RedirectBehavior(method, bodyBytes);
    }

    private static HttpRequest.BodyPublisher bodyPublisher(byte[] bodyBytes) {
        return bodyBytes.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
    }

    private static boolean skipRedirectHeader(String headerName) {
        return "content-length".equalsIgnoreCase(headerName) || "host".equalsIgnoreCase(headerName);
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode >= 300 && statusCode <= 399;
    }

    private static byte[] readResponseBodyBytes(InputStream inputStream) throws IOException, ResponseTooLargeException {
        if (inputStream == null) {
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int totalBytes = 0;
        while (true) {
            int read = inputStream.read(buffer);
            if (read == -1) {
                return outputStream.toByteArray();
            }
            totalBytes += read;
            if (totalBytes > MAX_RESPONSE_BODY_BYTES) {
                throw new ResponseTooLargeException();
            }
            outputStream.write(buffer, 0, read);
        }
    }

    private static List<HttpToolEntry> flattenHeaders(HttpHeaders headers) {
        List<HttpToolEntry> flattened = new ArrayList<>();
        headers.map().entrySet().stream()
                .sorted(comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    String normalizedName = entry.getKey().toLowerCase(Locale.ROOT);
                    for (String value : entry.getValue()) {
                        flattened.add(new HttpToolEntry(normalizedName, value));
                    }
                });
        return List.copyOf(flattened);
    }

    private static ResponseMetadata responseMetadata(HttpHeaders headers) {
        String rawContentType = headers.firstValue("content-type").orElse(null);
        String contentType = null;
        String charset = null;
        if (rawContentType != null && !rawContentType.isBlank()) {
            String[] parts = rawContentType.split(";");
            contentType = normalizeToken(parts[0]);
            for (int index = 1; index < parts.length; index++) {
                String part = parts[index].trim();
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if (!"charset".equals(name)) {
                    continue;
                }
                charset = normalizeToken(stripQuotes(part.substring(separator + 1).trim()));
                break;
            }
        }

        Long declaredContentLength = headers.firstValue("content-length")
                .flatMap(HttpToolExecution::parseNonNegativeLong)
                .orElse(null);
        return new ResponseMetadata(contentType, charset, declaredContentLength);
    }

    private static Optional<Long> parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0L ? Optional.empty() : Optional.of(parsed);
        } catch (NumberFormatException numberFormatException) {
            return Optional.empty();
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static DecodedBody decodeBody(String responseMode, byte[] responseBytes, ResponseMetadata metadata)
            throws LocalFailureException {
        if (responseBytes.length == 0) {
            return new DecodedBody(null, null);
        }

        if ("bytes".equals(responseMode)) {
            return new DecodedBody(null, Base64.getEncoder().encodeToString(responseBytes));
        }

        if ("text".equals(responseMode)) {
            return new DecodedBody(decodeText(responseBytes, metadata, "text"), null);
        }

        if ("json".equals(responseMode)) {
            String decodedText = decodeText(responseBytes, metadata, "json");
            try {
                JsonParser.parseString(decodedText);
            } catch (JsonParseException jsonParseException) {
                throw new LocalFailureException(
                        "unsupported_response_body",
                        "responseMode=json requires a valid JSON response body.",
                        false
                );
            }
            return new DecodedBody(decodedText, null);
        }

        if (isTextLike(metadata.contentType())) {
            try {
                return new DecodedBody(decodeText(responseBytes, metadata, "auto"), null);
            } catch (LocalFailureException localFailureException) {
                return new DecodedBody(null, Base64.getEncoder().encodeToString(responseBytes));
            }
        }

        return new DecodedBody(null, Base64.getEncoder().encodeToString(responseBytes));
    }

    private static String decodeText(byte[] responseBytes, ResponseMetadata metadata, String responseMode)
            throws LocalFailureException {
        Charset charset = resolveCharset(metadata.charset(), responseMode);
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(responseBytes));
            return decoded.toString();
        } catch (CharacterCodingException characterCodingException) {
            String message = "auto".equals(responseMode)
                    ? "response body could not be decoded as text."
                    : "responseMode=" + responseMode + " requires a decodable text response body.";
            throw new LocalFailureException("unsupported_response_body", message, false);
        }
    }

    private static Charset resolveCharset(String charsetName, String responseMode) throws LocalFailureException {
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            if ("auto".equals(responseMode)) {
                throw new LocalFailureException("unsupported_response_body",
                        "response body declared an unsupported charset.", false);
            }
            throw new LocalFailureException(
                    "unsupported_response_body",
                    "responseMode=" + responseMode + " requires a supported response charset.",
                    false
            );
        }
    }

    private static boolean isTextLike(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return contentType.startsWith("text/")
                || "application/json".equals(contentType)
                || contentType.endsWith("+json")
                || "application/xml".equals(contentType)
                || "text/xml".equals(contentType)
                || "application/javascript".equals(contentType)
                || "application/x-www-form-urlencoded".equals(contentType);
    }

    private static ToolResult errorWithPayload(HttpToolRequest request, String code, String message, boolean truncated) {
        String payloadJson = JsonSupport.GSON.toJson(new HttpToolResultEnvelope(
                request,
                new HttpToolFailure(code, message),
                truncated
        ));
        return ToolResult.error(payloadJson, code, message);
    }

    private static String failureCode(Throwable throwable) {
        if (hasCause(throwable, UnknownHostException.class)
                || hasCause(throwable, UnresolvedAddressException.class)
                || messageContains(throwable, "refused")
                || hasCause(throwable, ConnectException.class)) {
            return "tool_execution_failed";
        }
        return "tool_execution_failed";
    }

    private static String failureMessage(Throwable throwable) {
        if (hasCause(throwable, UnknownHostException.class) || hasCause(throwable, UnresolvedAddressException.class)) {
            return "HTTP request failed because the host could not be resolved.";
        }
        if (messageContains(throwable, "refused")) {
            return "HTTP request failed because the connection was refused.";
        }
        if (hasCause(throwable, ConnectException.class)) {
            return "HTTP request failed because the connection was refused.";
        }
        return "HTTP request execution failed.";
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return false;
    }

    private static boolean messageContains(Throwable throwable, String fragment) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        String normalizedFragment = fragment.toLowerCase(Locale.ROOT);
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(normalizedFragment)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return false;
    }

    private record RedirectBehavior(String method, byte[] bodyBytes) {
        private RedirectBehavior {
            method = Objects.requireNonNull(method, "method");
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }
    }

    private record RedirectedRequest(HttpRequest request, byte[] bodyBytes) {
        private RedirectedRequest {
            request = Objects.requireNonNull(request, "request");
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }
    }

    private record ResponseMetadata(String contentType, String charset, Long declaredContentLength) {
    }

    private record DecodedBody(String bodyText, String bodyBase64) {
    }

    private record ExecutedResponse(HttpToolResponse response, boolean truncated) {
    }

    private static final class ResponseTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class LocalFailureException extends Exception {
        private static final long serialVersionUID = 1L;

        private final String code;
        private final boolean truncated;

        private LocalFailureException(String code, String message, boolean truncated) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
            this.truncated = truncated;
        }

        private String code() {
            return code;
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
