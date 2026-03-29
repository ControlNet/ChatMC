package space.controlnet.chatmc.common.tools.mcp.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.tools.mcp.McpTransportException;
import space.controlnet.chatmc.core.tools.mcp.McpServerConfig;
import space.controlnet.chatmc.core.tools.mcp.McpTransportKind;
import space.controlnet.chatmc.core.util.JsonSupport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class McpStdioTransport implements AutoCloseable {
    private static final Duration PROCESS_EXIT_GRACE_PERIOD = Duration.ofMillis(250);

    private final String serverAlias;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final ExecutorService ioExecutor;
    private final boolean ownExecutor;
    private final Process process;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final ConcurrentMap<Long, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicReference<McpTransportException> terminalFailure = new AtomicReference<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Object writeLock = new Object();

    private McpStdioTransport(
            String serverAlias,
            Duration requestTimeout,
            int maxResponseBytes,
            ExecutorService ioExecutor,
            boolean ownExecutor,
            Process process
    ) {
        this.serverAlias = serverAlias;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.ioExecutor = ioExecutor;
        this.ownExecutor = ownExecutor;
        this.process = process;
        this.inputStream = process.getInputStream();
        this.outputStream = process.getOutputStream();
    }

    public static McpStdioTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                         int maxResponseBytes) throws McpTransportException {
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2, threadFactory(serverAlias));
        return open(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor, true);
    }

    public static McpStdioTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                         int maxResponseBytes, ExecutorService ioExecutor) throws McpTransportException {
        return open(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor, true);
    }

    private static McpStdioTransport open(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                          int maxResponseBytes, ExecutorService ioExecutor, boolean ownExecutor)
            throws McpTransportException {
        validateInputs(serverAlias, config, requestTimeout, maxResponseBytes, ioExecutor);
        Process process = null;
        try {
            process = createProcess(config);
            McpStdioTransport transport = new McpStdioTransport(
                    serverAlias,
                    requestTimeout,
                    maxResponseBytes,
                    ioExecutor,
                    ownExecutor,
                    process
            );
            transport.startReaderLoop();
            return transport;
        } catch (Exception exception) {
            destroyProcessQuietly(process);
            if (ownExecutor) {
                ioExecutor.shutdownNow();
            }
            throw McpTransportException.executionFailed(exception);
        }
    }

    public JsonObject request(String method, JsonObject params) throws McpTransportException {
        ensureUsable();
        long requestId = nextRequestId.getAndIncrement();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId);
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params.deepCopy());
        }

        CompletableFuture<JsonObject> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);
        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();

        try {
            await(sendMessage(request), deadlineNanos);
            return await(responseFuture, deadlineNanos);
        } catch (TimeoutException timeoutException) {
            pendingRequests.remove(requestId);
            McpTransportException timeoutFailure = McpTransportException.timeout(timeoutException);
            failTransport(timeoutFailure);
            throw timeoutFailure;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            pendingRequests.remove(requestId);
            throw McpTransportException.executionFailed(interruptedException);
        } catch (McpTransportException transportException) {
            pendingRequests.remove(requestId);
            throw transportException;
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
        try {
            await(sendMessage(request), deadlineNanos);
        } catch (TimeoutException timeoutException) {
            McpTransportException timeoutFailure = McpTransportException.timeout(timeoutException);
            failTransport(timeoutFailure);
            throw timeoutFailure;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw McpTransportException.executionFailed(interruptedException);
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }

        McpTransportException closeFailure = terminalFailure.get();
        if (closeFailure == null) {
            closeFailure = McpTransportException.executionFailed(null);
        }
        failPendingRequests(closeFailure);
        shutdownProcess();
        if (ownExecutor) {
            ioExecutor.shutdownNow();
        }
    }

    private void startReaderLoop() throws McpTransportException {
        try {
            ioExecutor.execute(this::readerLoop);
        } catch (RejectedExecutionException rejectedExecutionException) {
            throw McpTransportException.executionFailed(rejectedExecutionException);
        }
    }

    private void readerLoop() {
        try {
            while (!closing.get()) {
                String message = readMessageLine(inputStream, maxResponseBytes);
                if (message == null) {
                    if (!closing.get()) {
                        failTransport(McpTransportException.executionFailed(new EOFException(
                                "MCP stdio stream closed before a complete response was received."
                        )));
                    }
                    return;
                }
                if (message.isBlank()) {
                    continue;
                }

                JsonObject jsonMessage = parseMessage(message);
                handleIncomingMessage(jsonMessage);
            }
        } catch (Exception exception) {
            if (!closing.get()) {
                failTransport(exception instanceof McpTransportException transportException
                        ? transportException
                        : McpTransportException.executionFailed(exception));
            }
        }
    }

    private JsonObject parseMessage(String message) throws McpTransportException {
        try {
            JsonElement element = JsonParser.parseString(message);
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected JSON object message.");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException parseException) {
            throw McpTransportException.executionFailed(parseException);
        }
    }

    private void handleIncomingMessage(JsonObject message) {
        JsonElement idElement = message.get("id");
        JsonElement methodElement = message.get("method");
        if (methodElement != null && methodElement.isJsonPrimitive()
                && methodElement.getAsJsonPrimitive().isString()) {
            if (idElement != null && !idElement.isJsonNull()) {
                ChatMC.LOGGER.debug("Ignoring unsupported MCP server request from {}: {}",
                        serverAlias, methodElement.getAsString());
            }
            return;
        }

        Long requestId = parseRequestId(idElement);
        if (requestId == null) {
            return;
        }

        CompletableFuture<JsonObject> pendingFuture = pendingRequests.remove(requestId);
        if (pendingFuture != null) {
            pendingFuture.complete(message.deepCopy());
        }
    }

    private CompletableFuture<Void> sendMessage(JsonObject message) throws McpTransportException {
        try {
            return CompletableFuture.runAsync(() -> {
                String encoded = JsonSupport.GSON.toJson(message);
                byte[] bytes = (encoded + "\n").getBytes(StandardCharsets.UTF_8);
                synchronized (writeLock) {
                    try {
                        outputStream.write(bytes);
                        outputStream.flush();
                    } catch (IOException exception) {
                        McpTransportException writeFailure = McpTransportException.executionFailed(exception);
                        failTransport(writeFailure);
                        throw new RuntimeException(writeFailure);
                    }
                }
            }, ioExecutor);
        } catch (RejectedExecutionException rejectedExecutionException) {
            throw McpTransportException.executionFailed(rejectedExecutionException);
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
            if (cause instanceof RuntimeException runtimeException && runtimeException.getCause() instanceof McpTransportException transportException) {
                throw transportException;
            }
            throw McpTransportException.executionFailed(cause == null ? executionException : cause);
        }
    }

    private long remainingTimeout(long deadlineNanos) throws TimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            throw new TimeoutException("MCP stdio request timed out.");
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    private void ensureUsable() throws McpTransportException {
        McpTransportException failure = terminalFailure.get();
        if (failure != null) {
            throw failure;
        }
        if (closing.get()) {
            throw McpTransportException.executionFailed(null);
        }
    }

    private void failTransport(McpTransportException failure) {
        if (!terminalFailure.compareAndSet(null, failure)) {
            return;
        }
        closing.set(true);
        failPendingRequests(failure);
        shutdownProcess();
        if (ownExecutor) {
            ioExecutor.shutdownNow();
        }
    }

    private void failPendingRequests(McpTransportException failure) {
        for (CompletableFuture<JsonObject> pendingFuture : pendingRequests.values()) {
            pendingFuture.completeExceptionally(failure);
        }
        pendingRequests.clear();
    }

    private void shutdownProcess() {
        try {
            outputStream.close();
        } catch (IOException ignored) {
        }

        try {
            if (process.waitFor(PROCESS_EXIT_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return;
        }

        process.destroy();
        try {
            if (process.waitFor(PROCESS_EXIT_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return;
        }

        process.destroyForcibly();
        try {
            process.waitFor(PROCESS_EXIT_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static void validateInputs(String serverAlias, McpServerConfig config, Duration requestTimeout,
                                       int maxResponseBytes, ExecutorService ioExecutor) {
        if (serverAlias == null || serverAlias.isBlank()) {
            throw new IllegalArgumentException("MCP server alias is required.");
        }
        if (config == null) {
            throw new IllegalArgumentException("MCP server config is required.");
        }
        if (config.type() != McpTransportKind.STDIO) {
            throw new IllegalArgumentException("MCP stdio transport requires a stdio server config.");
        }
        if (config.command().filter(command -> !command.isBlank()).isEmpty()) {
            throw new IllegalArgumentException("MCP stdio command is required.");
        }
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("MCP stdio request timeout must be greater than zero.");
        }
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("MCP stdio maxResponseBytes must be greater than zero.");
        }
        Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    private static Process createProcess(McpServerConfig config) throws IOException {
        java.util.List<String> commandLine = new java.util.ArrayList<>();
        commandLine.add(config.command().orElseThrow(() -> new IllegalArgumentException("MCP stdio command is required.")));
        commandLine.addAll(config.args());

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        if (config.cwd().isPresent()) {
            processBuilder.directory(Path.of(config.cwd().orElseThrow()).toFile());
        }
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().putAll(config.env());
        return processBuilder.start();
    }

    private static ThreadFactory threadFactory(String serverAlias) {
        AtomicLong counter = new AtomicLong(1L);
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "chatmc-mcp-stdio-" + serverAlias + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Long parseRequestId(JsonElement idElement) {
        if (idElement == null || idElement.isJsonNull() || !idElement.isJsonPrimitive()) {
            return null;
        }
        try {
            return idElement.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static String readMessageLine(InputStream inputStream, int maxResponseBytes) throws IOException {
        byte[] buffer = new byte[maxResponseBytes + 1];
        int length = 0;
        while (true) {
            int nextByte = inputStream.read();
            if (nextByte == -1) {
                if (length == 0) {
                    return null;
                }
                throw new EOFException("MCP stdio stream ended mid-message.");
            }
            if (nextByte == '\n') {
                return new String(buffer, 0, length, StandardCharsets.UTF_8);
            }
            if (nextByte == '\r') {
                continue;
            }
            if (length >= maxResponseBytes) {
                throw new IOException("MCP stdio response exceeded maxResponseBytes=" + maxResponseBytes + ".");
            }
            buffer[length++] = (byte) nextByte;
        }
    }

    private static void destroyProcessQuietly(Process process) {
        if (process == null) {
            return;
        }
        process.destroyForcibly();
    }
}
