package space.controlnet.mineagent.common.tools.mcp.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import space.controlnet.mineagent.common.tools.mcp.McpTransportException;
import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;
import space.controlnet.mineagent.core.tools.mcp.McpTransportKind;
import space.controlnet.mineagent.core.util.JsonSupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class McpHttpTransport implements AutoCloseable {
    private static final String ACCEPT_HEADER_VALUE = "application/json, text/event-stream";

    private final String serverAlias;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final ExecutorService ioExecutor;
    private final boolean ownExecutor;
    private final HttpClient httpClient;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicReference<String> protocolVersion = new AtomicReference<>();

    private McpHttpTransport(
            String serverAlias,
            URI endpoint,
            Duration requestTimeout,
            int maxResponseBytes,
            ExecutorService ioExecutor,
            boolean ownExecutor,
            HttpClient httpClient
    ) {
        this.serverAlias = serverAlias;
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.ioExecutor = ioExecutor;
        this.ownExecutor = ownExecutor;
        this.httpClient = httpClient;
    }

    public static McpHttpTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                        int maxResponseBytes) {
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2, threadFactory(serverAlias));
        return open(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor, true);
    }

    public static McpHttpTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                        int maxResponseBytes, ExecutorService ioExecutor) {
        return open(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor, true);
    }

    public JsonObject request(String method, JsonObject params) throws McpTransportException {
        ensureUsable();

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", nextRequestId.getAndIncrement());
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params.deepCopy());
        }

        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();
        CompletableFuture<HttpJsonResponse> responseFuture = execute(request, true);
        try {
            HttpJsonResponse response = await(responseFuture, deadlineNanos);
            if ("initialize".equals(method)) {
                sessionId.set(extractSessionId(response.headers()));
            }
            return response.body();
        } catch (TimeoutException timeoutException) {
            responseFuture.cancel(true);
            throw McpTransportException.timeout(timeoutException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            responseFuture.cancel(true);
            throw McpTransportException.executionFailed(interruptedException);
        }
    }

    public void notify(String method, JsonObject params) throws McpTransportException {
        ensureUsable();

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params.deepCopy());
        }

        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();
        CompletableFuture<HttpJsonResponse> responseFuture = execute(request, false);
        try {
            await(responseFuture, deadlineNanos);
        } catch (TimeoutException timeoutException) {
            responseFuture.cancel(true);
            throw McpTransportException.timeout(timeoutException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            responseFuture.cancel(true);
            throw McpTransportException.executionFailed(interruptedException);
        }
    }

    public void setProtocolVersion(String negotiatedProtocolVersion) {
        if (negotiatedProtocolVersion == null || negotiatedProtocolVersion.isBlank()) {
            throw new IllegalArgumentException("MCP negotiated protocol version is required.");
        }
        protocolVersion.set(negotiatedProtocolVersion);
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        if (ownExecutor) {
            ioExecutor.shutdownNow();
        }
    }

    private static McpHttpTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                         int maxResponseBytes, ExecutorService ioExecutor, boolean ownExecutor) {
        URI endpoint = validateInputs(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .executor(ioExecutor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new McpHttpTransport(serverAlias, endpoint, requestTimeout, maxResponseBytes, ioExecutor, ownExecutor,
                httpClient);
    }

    private CompletableFuture<HttpJsonResponse> execute(JsonObject message, boolean responseExpected)
            throws McpTransportException {
        try {
            HttpRequest httpRequest = buildRequest(message);
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApplyAsync(response -> readResponse(message, response, responseExpected), ioExecutor);
        } catch (RejectedExecutionException rejectedExecutionException) {
            throw McpTransportException.executionFailed(rejectedExecutionException);
        }
    }

    private HttpRequest buildRequest(JsonObject message) {
        byte[] requestBytes = JsonSupport.GSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                .header("Accept", ACCEPT_HEADER_VALUE)
                .header("Content-Type", "application/json");

        Optional.ofNullable(protocolVersion.get())
                .filter(value -> !value.isBlank())
                .ifPresent(value -> builder.header("MCP-Protocol-Version", value));
        Optional.ofNullable(sessionId.get())
                .filter(value -> !value.isBlank())
                .ifPresent(value -> builder.header("MCP-Session-Id", value));
        return builder.build();
    }

    private HttpJsonResponse readResponse(JsonObject requestMessage, HttpResponse<InputStream> response,
                                          boolean responseExpected) {
        try (InputStream responseBody = response.body()) {
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                readBodyBytes(responseBody);
                throw new IllegalStateException("MCP HTTP server '" + serverAlias + "' returned HTTP "
                        + statusCode + " for method '" + methodName(requestMessage) + "'.");
            }

            byte[] responseBytes = readBodyBytes(responseBody);
            if (!responseExpected) {
                return new HttpJsonResponse(null, response.headers());
            }

            String contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .orElse("");
            if (contentType.startsWith("text/event-stream")) {
                throw new IllegalStateException(
                        "MCP HTTP server '" + serverAlias + "' returned SSE, which MineAgent v1 does not support."
                );
            }
            if (!contentType.isEmpty() && !contentType.startsWith("application/json")) {
                throw new IllegalStateException("MCP HTTP server '" + serverAlias + "' returned unsupported content type '"
                        + response.headers().firstValue("Content-Type").orElse(contentType)
                        + "' for method '" + methodName(requestMessage) + "'.");
            }
            if (responseBytes.length == 0) {
                throw new IllegalStateException("MCP HTTP server '" + serverAlias + "' returned an empty response body for method '"
                        + methodName(requestMessage) + "'.");
            }

            JsonElement element = JsonParser.parseString(new String(responseBytes, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected JSON object response.");
            }
            return new HttpJsonResponse(element.getAsJsonObject(), response.headers());
        } catch (JsonParseException | IllegalStateException exception) {
            throw new java.util.concurrent.CompletionException(McpTransportException.executionFailed(exception));
        } catch (IOException ioException) {
            throw new java.util.concurrent.CompletionException(McpTransportException.executionFailed(ioException));
        }
    }

    private byte[] readBodyBytes(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        while (true) {
            int read = inputStream.read(buffer);
            if (read == -1) {
                return output.toByteArray();
            }
            totalBytes += read;
            if (totalBytes > maxResponseBytes) {
                throw new IOException("MCP HTTP response exceeded maxResponseBytes=" + maxResponseBytes + ".");
            }
            output.write(buffer, 0, read);
        }
    }

    private <T> T await(CompletableFuture<T> future, long deadlineNanos)
            throws TimeoutException, InterruptedException, McpTransportException {
        try {
            return future.get(remainingTimeout(deadlineNanos), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof McpTransportException transportException) {
                throw transportException;
            }
            if (cause instanceof java.util.concurrent.CompletionException completionException
                    && completionException.getCause() instanceof McpTransportException transportException) {
                throw transportException;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
                throw McpTransportException.timeout(cause);
            }
            throw McpTransportException.executionFailed(cause == null ? executionException : cause);
        }
    }

    private long remainingTimeout(long deadlineNanos) throws TimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            throw new TimeoutException("MCP HTTP request timed out.");
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    private void ensureUsable() throws McpTransportException {
        if (closing.get()) {
            throw McpTransportException.executionFailed(null);
        }
    }

    private static URI validateInputs(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                      int maxResponseBytes, ExecutorService ioExecutor) {
        if (serverAlias == null || serverAlias.isBlank()) {
            throw new IllegalArgumentException("MCP server alias is required.");
        }
        if (config == null) {
            throw new IllegalArgumentException("MCP server config is required.");
        }
        if (config.type() != McpTransportKind.HTTP) {
            throw new IllegalArgumentException("MCP HTTP transport requires an http server config.");
        }
        String url = config.url().filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("MCP HTTP url is required."));
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("MCP HTTP request timeout must be greater than zero.");
        }
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("MCP HTTP maxResponseBytes must be greater than zero.");
        }
        Objects.requireNonNull(ioExecutor, "ioExecutor");
        return URI.create(url);
    }

    private static ThreadFactory threadFactory(String serverAlias) {
        AtomicLong counter = new AtomicLong(1L);
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "mineagent-mcp-http-" + serverAlias + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String extractSessionId(HttpHeaders headers) throws McpTransportException {
        String raw = headers.firstValue("MCP-Session-Id").orElse(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        for (int index = 0; index < raw.length(); index++) {
            char value = raw.charAt(index);
            if (value < 0x21 || value > 0x7E) {
                throw McpTransportException.executionFailed(new IllegalStateException(
                        "MCP HTTP session id contains unsupported characters."
                ));
            }
        }
        return raw;
    }

    private static String methodName(JsonObject requestMessage) {
        JsonElement methodElement = requestMessage.get("method");
        if (methodElement != null && methodElement.isJsonPrimitive() && methodElement.getAsJsonPrimitive().isString()) {
            return methodElement.getAsString();
        }
        return "unknown";
    }

    private record HttpJsonResponse(JsonObject body, HttpHeaders headers) {
    }
}
