package space.controlnet.chatmc.common.agent;

import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.testing.DeterministicBarrier;
import space.controlnet.chatmc.common.testing.ThreadConfinementAssertions;
import space.controlnet.chatmc.common.testing.TimeoutUtility;
import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.AgentToolSpec;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.time.Duration;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ThreadConfinementRegressionTest {
    @Test
    void task14_mcSessionContext_serverUnavailable_returnsDeterministicFailure() {
        ensureMinecraftBootstrap();
        ChatMCNetwork.setServer(null);
        Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000001401"));
        Object call = newToolCall(uniqueName("tool"), "{}");

        Object first = invokeMcSessionExecuteTool(context, call, true);
        Object second = invokeMcSessionExecuteTool(context, call, true);

        assertOutcomeErrorCode("task14/base/server-missing/first", first, "tool_execution_failed");
        assertOutcomeErrorCode("task14/base/server-missing/second", second, "tool_execution_failed");
    }

    @Test
    void task14_mcSessionContext_serverUnavailable_preemptsToolRegistryUnknownTool() {
        ensureMinecraftBootstrap();
        ChatMCNetwork.setServer(null);
        Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000001402"));
        Object call = newToolCall(uniqueName("unknown"), "{}");

        Object directRegistry = invokeToolRegistryExecuteTool(call, true);
        Object throughSessionContext = invokeMcSessionExecuteTool(context, call, true);

        assertOutcomeErrorCode("task14/base/direct-registry", directRegistry, "unknown_tool");
        assertOutcomeErrorCode("task14/base/server-missing-preempts-registry", throughSessionContext,
                "tool_execution_failed");
    }

    @Test
    void task14_toolRegistry_dispatch_executesOnCallingThread() {
        String toolName = uniqueName("dispatch-tool");
        String providerId = uniqueName("provider");
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        AtomicInteger executeCount = new AtomicInteger();
        DeterministicBarrier dispatchBarrier = new DeterministicBarrier("task14/base/registry-dispatch/provider-entered");

        registerThreadCapturingProvider(providerId, toolName, providerThread, executeCount, dispatchBarrier);

        try {
            AtomicReference<Object> outcomeRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    Object call = newToolCall(toolName, "{}");
                    outcomeRef.set(invokeToolRegistryExecuteTool(call, true));
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                }
            }, uniqueName("worker"));

            worker.start();
            dispatchBarrier.awaitArrivals(1, Duration.ofSeconds(1));
            assertTrue("task14/base/registry-dispatch/outcome-before-release", outcomeRef.get() == null);
            dispatchBarrier.release();
            TimeoutUtility.awaitThreadCompletion("task14/base/registry-dispatch", worker, Duration.ofSeconds(2));
            if (errorRef.get() != null) {
                throw new AssertionError("task14/base/registry-dispatch/unexpected-error", errorRef.get());
            }

            assertEquals("task14/base/registry-dispatch/execute-count", 1, executeCount.get());
            ThreadConfinementAssertions.assertSameThread(
                    "task14/base/registry-dispatch/provider-thread",
                    worker,
                    requireNonNull("task14/base/registry-dispatch/provider-thread-present", providerThread.get())
            );
            assertOutcomeSuccess("task14/base/registry-dispatch/outcome", outcomeRef.get());
        } finally {
            ToolRegistry.unregister(providerId);
        }
    }

    @Test
    void task14_timeoutFailureContract_presentInAgentRunnerSource() {
        String source = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/McSessionContext.java");

        assertContains("task14/base/timeout-contract/timeout-constant", source,
                "TOOL_EXECUTION_TIMEOUT_MS = 30_000L");
        assertContains("task14/base/timeout-contract/timeout-branch", source,
                "ToolResult.error(\"tool_timeout\", \"tool execution timeout\")");
        assertContains("task14/base/timeout-contract/failure-branch", source,
                "ToolResult.error(\"tool_execution_failed\", \"tool execution failed\")");
    }

    @Test
    void task17_callingThreadAffinity_allowsExecutionWithoutServerThread() {
        ensureMinecraftBootstrap();
        ChatMCNetwork.setServer(null);

        String providerId = uniqueName("provider");
        String toolName = uniqueName("mcp-tool");
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        AtomicInteger executeCount = new AtomicInteger();
        ToolRegistry.register(providerId, new ThreadCapturingToolProvider(
                toolName,
                ToolProvider.ExecutionAffinity.CALLING_THREAD,
                providerThread,
                executeCount,
                null
        ));

        try {
            Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000001701"));
            AtomicReference<Object> outcomeRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    outcomeRef.set(invokeMcSessionExecuteTool(context, newToolCall(toolName, "{}"), true));
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                }
            }, uniqueName("caller-worker"));

            worker.start();
            TimeoutUtility.awaitThreadCompletion("task17/base/calling-thread-affinity", worker, Duration.ofSeconds(2));
            if (errorRef.get() != null) {
                throw new AssertionError("task17/base/calling-thread-affinity/unexpected-error", errorRef.get());
            }

            assertEquals("task17/base/calling-thread-affinity/execute-count", 1, executeCount.get());
            assertEquals("task17/base/calling-thread-affinity/registry-affinity",
                    ToolProvider.ExecutionAffinity.CALLING_THREAD,
                    ToolRegistry.getExecutionAffinity(toolName));
            ThreadConfinementAssertions.assertSameThread(
                    "task17/base/calling-thread-affinity/provider-thread",
                    worker,
                    requireNonNull("task17/base/calling-thread-affinity/provider-thread-present", providerThread.get())
            );
            assertOutcomeSuccess("task17/base/calling-thread-affinity/outcome", outcomeRef.get());
        } finally {
            ToolRegistry.unregister(providerId);
        }
    }

    @Test
    void task17_defaultAffinity_withoutServer_preservesFailureBoundary() {
        ensureMinecraftBootstrap();
        ChatMCNetwork.setServer(null);

        String providerId = uniqueName("provider");
        String toolName = uniqueName("server-bound-tool");
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        AtomicInteger executeCount = new AtomicInteger();
        ToolRegistry.register(providerId, new ThreadCapturingToolProvider(
                toolName,
                ToolProvider.ExecutionAffinity.SERVER_THREAD,
                providerThread,
                executeCount,
                null
        ));

        try {
            Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000001702"));
            Object outcome = invokeMcSessionExecuteTool(context, newToolCall(toolName, "{}"), true);

            assertEquals("task17/base/default-affinity/registry-affinity",
                    ToolProvider.ExecutionAffinity.SERVER_THREAD,
                    ToolRegistry.getExecutionAffinity(toolName));
            assertOutcomeErrorCode("task17/base/default-affinity/outcome", outcome, "tool_execution_failed");
            assertEquals("task17/base/default-affinity/execute-count", 0, executeCount.get());
            assertTrue("task17/base/default-affinity/provider-thread-absent", providerThread.get() == null);
        } finally {
            ToolRegistry.unregister(providerId);
        }
    }

    private static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.chatmc.common.agent.McSessionContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return constructor.newInstance(playerId);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/new-session-context", exception);
        }
    }

    private static Object newToolCall(String toolName, String argsJson) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Constructor<?> constructor = toolCallClass.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/new-tool-call", exception);
        }
    }

    private static Object invokeMcSessionExecuteTool(Object context, Object call, boolean approved) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Method method = context.getClass().getDeclaredMethod("executeTool", Optional.class, toolCallClass,
                    boolean.class);
            method.setAccessible(true);
            return method.invoke(context, Optional.empty(), call, approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/invoke-session-execute", exception);
        }
    }

    private static Object invokeToolRegistryExecuteTool(Object call, boolean approved) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Method method = ToolRegistry.class.getMethod("executeTool", Optional.class, toolCallClass, boolean.class);
            return method.invoke(null, Optional.empty(), call, approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/invoke-registry-execute", exception);
        }
    }

    private static void registerThreadCapturingProvider(
            String providerId,
            String toolName,
            AtomicReference<Thread> providerThread,
            AtomicInteger executeCount,
            DeterministicBarrier dispatchBarrier
    ) {
        ToolRegistry.register(providerId, new ThreadCapturingToolProvider(
                toolName,
                ToolProvider.ExecutionAffinity.SERVER_THREAD,
                providerThread,
                executeCount,
                dispatchBarrier
        ));
    }

    private static void assertOutcomeSuccess(String assertionName, Object outcome) {
        Object nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        Object result = invokeZeroArg(nonNullOutcome, "result", assertionName + "/result");
        boolean success = (Boolean) invokeZeroArg(result, "success", assertionName + "/success");
        assertTrue(assertionName + "/success-flag", success);
    }

    private static void assertOutcomeErrorCode(String assertionName, Object outcome, String expectedCode) {
        Object nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        Object result = invokeZeroArg(nonNullOutcome, "result", assertionName + "/result");
        boolean success = (Boolean) invokeZeroArg(result, "success", assertionName + "/success");
        assertTrue(assertionName + "/must-be-failure", !success);
        Object error = invokeZeroArg(result, "error", assertionName + "/error");
        String code = (String) invokeZeroArg(error, "code", assertionName + "/code");
        assertEquals(assertionName + "/error-code", expectedCode, code);
    }

    private static Object invokeZeroArg(Object target, String methodName, String assertionName) {
        Object nonNullTarget = requireNonNull(assertionName + "/target", target);
        try {
            Method method = nonNullTarget.getClass().getMethod(methodName);
            return method.invoke(nonNullTarget);
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> invoke " + methodName + " failed", exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(assertionName + " -> expected to find: " + needle);
        }
    }

    private static String readSource(String path) {
        try {
            Path direct = Path.of(path);
            if (Files.exists(direct)) {
                return Files.readString(direct);
            }

            Path fromModule = Path.of("..").resolve("..").resolve(path).normalize();
            if (Files.exists(fromModule)) {
                return Files.readString(fromModule);
            }

            throw new AssertionError("read-source missing: " + path + " (checked " + direct + " and " + fromModule + ")");
        } catch (Exception exception) {
            throw new AssertionError("read-source failed: " + path, exception);
        }
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/bootstrap", exception);
        }
    }

    private static String uniqueName(String prefix) {
        return prefix + "-task14-" + UUID.randomUUID();
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (!value) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class ThreadCapturingToolProvider implements ToolProvider {
        private final AgentTool toolSpec;
        private final ToolProvider.ExecutionAffinity affinity;
        private final AtomicReference<Thread> providerThread;
        private final AtomicInteger executeCount;
        private final DeterministicBarrier dispatchBarrier;

        private ThreadCapturingToolProvider(
                String toolName,
                ToolProvider.ExecutionAffinity affinity,
                AtomicReference<Thread> providerThread,
                AtomicInteger executeCount,
                DeterministicBarrier dispatchBarrier
        ) {
            this.toolSpec = AgentToolSpec.metadataOnly(toolName, "", "{}", List.of(), "", List.of(), List.of());
            this.affinity = affinity;
            this.providerThread = providerThread;
            this.executeCount = executeCount;
            this.dispatchBarrier = dispatchBarrier;
        }

        @Override
        public List<AgentTool> specs() {
            return List.of(toolSpec);
        }

        @Override
        public ToolProvider.ExecutionAffinity executionAffinity() {
            return affinity;
        }

        @Override
        public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            executeCount.incrementAndGet();
            providerThread.set(Thread.currentThread());
            if (dispatchBarrier != null) {
                dispatchBarrier.arriveAndAwaitRelease("provider-execute", Duration.ofSeconds(1));
            }
            return ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
        }
    }
}
