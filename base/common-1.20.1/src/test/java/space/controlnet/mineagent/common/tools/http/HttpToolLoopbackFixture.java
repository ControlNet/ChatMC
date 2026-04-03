package space.controlnet.mineagent.common.tools.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import space.controlnet.mineagent.common.testing.TimeoutUtility;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpToolLoopbackFixture implements AutoCloseable {
    private static final AtomicInteger FIXTURE_IDS = new AtomicInteger();

    private final HttpServer server;
    private final ExecutorService executor;
    private final String fixtureName;
    private final Map<String, CopyOnWriteArrayList<RecordedExchange>> exchangesByPath;
    private final AtomicBoolean closed;

    private HttpToolLoopbackFixture(HttpServer server, ExecutorService executor, String fixtureName) {
        this.server = server;
        this.executor = executor;
        this.fixtureName = fixtureName;
        this.exchangesByPath = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean(false);
    }

    public static HttpToolLoopbackFixture create(String fixtureName) throws IOException {
        String normalizedName = requireName(fixtureName);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool(threadFactory(normalizedName));
        server.setExecutor(executor);
        HttpToolLoopbackFixture fixture = new HttpToolLoopbackFixture(server, executor, normalizedName);
        server.start();
        return fixture;
    }

    public HttpToolLoopbackFixture addFixedResponse(String path, FixedResponse response) {
        FixedResponse nonNullResponse = Objects.requireNonNull(response, "response must not be null");
        return register(path, request -> nonNullResponse.toEmittedResponse());
    }

    public HttpToolLoopbackFixture addEchoResponse(String path, EchoResponse response) {
        EchoResponse nonNullResponse = Objects.requireNonNull(response, "response must not be null");
        return register(path, request -> nonNullResponse.toEmittedResponse(request.bodyBytes()));
    }

    public HttpToolLoopbackFixture addRedirect(String path, RedirectResponse response) {
        RedirectResponse nonNullResponse = Objects.requireNonNull(response, "response must not be null");
        return register(path, request -> nonNullResponse.toEmittedResponse());
    }

    public HttpToolLoopbackFixture addRedirectChain(List<RedirectStep> redirectSteps,
                                                    String terminalPath,
                                                    FixedResponse terminalResponse) {
        List<RedirectStep> normalizedSteps = List.copyOf(Objects.requireNonNull(redirectSteps,
                "redirectSteps must not be null"));
        if (normalizedSteps.isEmpty()) {
            throw new IllegalArgumentException("redirectSteps must not be empty");
        }

        for (RedirectStep redirectStep : normalizedSteps) {
            addRedirect(redirectStep.path(), new RedirectResponse(
                    redirectStep.statusCode(),
                    redirectStep.location(),
                    List.of(),
                    Duration.ZERO
            ));
        }
        return addFixedResponse(terminalPath, Objects.requireNonNull(terminalResponse,
                "terminalResponse must not be null"));
    }

    public URI uri(String path) {
        String normalizedPath = normalizePath(path);
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + normalizedPath);
    }

    public List<RecordedExchange> recordedExchanges(String path) {
        String normalizedPath = normalizePath(path);
        CopyOnWriteArrayList<RecordedExchange> exchanges = exchangesByPath.get(normalizedPath);
        if (exchanges == null) {
            return List.of();
        }
        return List.copyOf(exchanges);
    }

    public RecordedExchange lastRecordedExchange(String path) {
        List<RecordedExchange> exchanges = recordedExchanges(path);
        if (exchanges.isEmpty()) {
            throw new IllegalStateException("No exchanges recorded for path: " + normalizePath(path));
        }
        return exchanges.get(exchanges.size() - 1);
    }

    public void awaitRequestCount(String path, int expectedCount, Duration timeout) {
        String normalizedPath = normalizePath(path);
        if (expectedCount < 1) {
            throw new IllegalArgumentException("expectedCount must be greater than zero");
        }
        TimeoutUtility.await(fixtureName + normalizedPath + "/request-count", timeout,
                () -> recordedExchanges(normalizedPath).size() >= expectedCount);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        server.stop(0);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError(fixtureName + "/executor-shutdown -> timed out waiting for fixture executor");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError(fixtureName + "/executor-shutdown-interrupted", interruptedException);
        }
    }

    private HttpToolLoopbackFixture register(String path, ExchangeBehavior behavior) {
        ensureOpen();
        String normalizedPath = normalizePath(path);
        ExchangeBehavior nonNullBehavior = Objects.requireNonNull(behavior, "behavior must not be null");
        if (exchangesByPath.putIfAbsent(normalizedPath, new CopyOnWriteArrayList<>()) != null) {
            throw new IllegalArgumentException("Path is already registered: " + normalizedPath);
        }
        server.createContext(normalizedPath, exchange -> handle(normalizedPath, nonNullBehavior, exchange));
        return this;
    }

    private void handle(String path, ExchangeBehavior behavior, HttpExchange exchange) throws IOException {
        try {
            RecordedExchange request = recordExchange(exchange);
            exchangesByPath.get(path).add(request);
            EmittedResponse response = behavior.apply(request);
            pause(response.delay());
            if (closed.get()) {
                return;
            }
            sendResponse(exchange, response);
        } finally {
            exchange.close();
        }
    }

    private RecordedExchange recordExchange(HttpExchange exchange) throws IOException {
        return new RecordedExchange(
                exchange.getRequestMethod(),
                exchange.getRequestURI(),
                flattenHeaders(exchange.getRequestHeaders()),
                exchange.getRequestBody().readAllBytes()
        );
    }

    private void sendResponse(HttpExchange exchange, EmittedResponse response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        for (HttpToolEntry header : response.headers()) {
            headers.add(header.name(), header.value());
        }

        byte[] bodyBytes = response.bodyBytes();
        if (mustOmitResponseBody(exchange.getRequestMethod(), response.statusCode())) {
            exchange.sendResponseHeaders(response.statusCode(), -1);
            return;
        }

        exchange.sendResponseHeaders(response.statusCode(), bodyBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            if (bodyBytes.length > 0) {
                outputStream.write(bodyBytes);
            }
        }
    }

    private void pause(Duration delay) {
        if (delay.isZero()) {
            return;
        }

        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            if (closed.get()) {
                return;
            }
            throw new AssertionError(fixtureName + "/delay-interrupted", interruptedException);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Fixture is already closed.");
        }
    }

    private static List<HttpToolEntry> flattenHeaders(Headers headers) {
        List<HttpToolEntry> flattened = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                flattened.add(new HttpToolEntry(entry.getKey(), value));
            }
        }
        return List.copyOf(flattened);
    }

    private static boolean hasHeader(List<HttpToolEntry> headers, String headerName) {
        for (HttpToolEntry header : headers) {
            if (header.name().equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private static List<HttpToolEntry> ensureHeader(List<HttpToolEntry> headers, String name, String value) {
        if (hasHeader(headers, name)) {
            return headers;
        }

        ArrayList<HttpToolEntry> normalized = new ArrayList<>(headers);
        normalized.add(new HttpToolEntry(name, value));
        return List.copyOf(normalized);
    }

    private static boolean mustOmitResponseBody(String requestMethod, int statusCode) {
        return "HEAD".equalsIgnoreCase(requestMethod) || statusCode == 204 || statusCode == 304;
    }

    private static ThreadFactory threadFactory(String fixtureName) {
        AtomicInteger threadIds = new AtomicInteger();
        int fixtureId = FIXTURE_IDS.incrementAndGet();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "http-tool-fixture-" + fixtureId + "-" + fixtureName + "-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String requireName(String fixtureName) {
        if (fixtureName == null || fixtureName.isBlank()) {
            throw new IllegalArgumentException("fixtureName must not be blank");
        }
        return fixtureName;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'.");
        }
        return path;
    }

    private static int validateStatusCode(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599.");
        }
        return statusCode;
    }

    private static Duration normalizeDelay(Duration delay) {
        if (delay == null) {
            return Duration.ZERO;
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative.");
        }
        return delay;
    }

    private interface ExchangeBehavior {
        EmittedResponse apply(RecordedExchange request);
    }

    public record FixedResponse(int statusCode, List<HttpToolEntry> headers, byte[] bodyBytes, Duration delay) {
        public FixedResponse {
            statusCode = validateStatusCode(statusCode);
            headers = List.copyOf(headers == null ? List.of() : headers);
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
            delay = normalizeDelay(delay);
        }

        public static FixedResponse binary(int statusCode, byte[] bodyBytes, List<HttpToolEntry> headers) {
            return new FixedResponse(statusCode,
                    ensureHeader(List.copyOf(headers == null ? List.of() : headers),
                            "Content-Type",
                            "application/octet-stream"),
                    bodyBytes,
                    Duration.ZERO);
        }

        public static FixedResponse text(int statusCode, String bodyText, Charset charset, List<HttpToolEntry> headers) {
            Charset nonNullCharset = Objects.requireNonNull(charset, "charset must not be null");
            String normalizedBodyText = bodyText == null ? "" : bodyText;
            return new FixedResponse(statusCode,
                    ensureHeader(List.copyOf(headers == null ? List.of() : headers),
                            "Content-Type",
                            "text/plain; charset=" + nonNullCharset.name().toLowerCase(Locale.ROOT)),
                    normalizedBodyText.getBytes(nonNullCharset),
                    Duration.ZERO);
        }

        public FixedResponse withDelay(Duration delay) {
            return new FixedResponse(statusCode, headers, bodyBytes, delay);
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }

        private EmittedResponse toEmittedResponse() {
            return new EmittedResponse(statusCode, headers, bodyBytes, delay);
        }
    }

    public record EchoResponse(int statusCode, List<HttpToolEntry> headers, Duration delay) {
        public EchoResponse {
            statusCode = validateStatusCode(statusCode);
            headers = List.copyOf(headers == null ? List.of() : headers);
            delay = normalizeDelay(delay);
        }

        public static EchoResponse binary(int statusCode, List<HttpToolEntry> headers) {
            return new EchoResponse(statusCode,
                    ensureHeader(List.copyOf(headers == null ? List.of() : headers),
                            "Content-Type",
                            "application/octet-stream"),
                    Duration.ZERO);
        }

        public static EchoResponse text(int statusCode, Charset charset, List<HttpToolEntry> headers) {
            Charset nonNullCharset = Objects.requireNonNull(charset, "charset must not be null");
            return new EchoResponse(statusCode,
                    ensureHeader(List.copyOf(headers == null ? List.of() : headers),
                            "Content-Type",
                            "text/plain; charset=" + nonNullCharset.name().toLowerCase(Locale.ROOT)),
                    Duration.ZERO);
        }

        public EchoResponse withDelay(Duration delay) {
            return new EchoResponse(statusCode, headers, delay);
        }

        private EmittedResponse toEmittedResponse(byte[] requestBody) {
            return new EmittedResponse(statusCode, headers, requestBody, delay);
        }
    }

    public record RedirectResponse(int statusCode, String location, List<HttpToolEntry> headers, Duration delay) {
        public RedirectResponse {
            statusCode = validateStatusCode(statusCode);
            if (statusCode < 300 || statusCode > 399) {
                throw new IllegalArgumentException("redirect statusCode must be between 300 and 399.");
            }
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            headers = ensureHeader(List.copyOf(headers == null ? List.of() : headers), "Location", location);
            delay = normalizeDelay(delay);
        }

        public RedirectResponse withDelay(Duration delay) {
            return new RedirectResponse(statusCode, location, headers, delay);
        }

        private EmittedResponse toEmittedResponse() {
            return new EmittedResponse(statusCode, headers, new byte[0], delay);
        }
    }

    public record RedirectStep(String path, int statusCode, String location) {
        public RedirectStep {
            path = normalizePath(path);
            validateStatusCode(statusCode);
            if (statusCode < 300 || statusCode > 399) {
                throw new IllegalArgumentException("redirect statusCode must be between 300 and 399.");
            }
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
        }
    }

    public record RecordedExchange(String method, URI requestUri, List<HttpToolEntry> headers, byte[] bodyBytes) {
        public RecordedExchange {
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("method must not be blank");
            }
            requestUri = Objects.requireNonNull(requestUri, "requestUri must not be null");
            headers = List.copyOf(headers == null ? List.of() : headers);
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
        }

        public String path() {
            return requestUri.getPath();
        }

        public String rawQuery() {
            return requestUri.getRawQuery();
        }

        public String bodyText(Charset charset) {
            Charset nonNullCharset = Objects.requireNonNull(charset, "charset must not be null");
            return new String(bodyBytes, nonNullCharset);
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }
    }

    private record EmittedResponse(int statusCode, List<HttpToolEntry> headers, byte[] bodyBytes, Duration delay) {
        private EmittedResponse {
            statusCode = validateStatusCode(statusCode);
            headers = List.copyOf(headers == null ? List.of() : headers);
            bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
            delay = normalizeDelay(delay);
        }

        @Override
        public byte[] bodyBytes() {
            return bodyBytes.clone();
        }
    }
}
