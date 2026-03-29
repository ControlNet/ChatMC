package space.controlnet.chatmc.common.tools.mcp;

import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.common.ChatMCNetwork;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolExecutionAffinityRegressionTest {
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
                executeCount
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
            TimeoutUtility.awaitThreadCompletion("task17/mcp/calling-thread-affinity", worker, Duration.ofSeconds(2));
            if (errorRef.get() != null) {
                throw new AssertionError("task17/mcp/calling-thread-affinity/unexpected-error", errorRef.get());
            }

            assertEquals("task17/mcp/calling-thread-affinity/execute-count", 1, executeCount.get());
            assertEquals("task17/mcp/calling-thread-affinity/registry-affinity",
                    ToolProvider.ExecutionAffinity.CALLING_THREAD,
                    ToolRegistry.getExecutionAffinity(toolName));
            ThreadConfinementAssertions.assertSameThread(
                    "task17/mcp/calling-thread-affinity/provider-thread",
                    worker,
                    requireNonNull("task17/mcp/calling-thread-affinity/provider-thread-present", providerThread.get())
            );
            assertOutcomeSuccess("task17/mcp/calling-thread-affinity/outcome", outcomeRef.get());
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
                executeCount
        ));

        try {
            Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000001702"));
            Object outcome = invokeMcSessionExecuteTool(context, newToolCall(toolName, "{}"), true);

            assertEquals("task17/mcp/default-affinity/registry-affinity",
                    ToolProvider.ExecutionAffinity.SERVER_THREAD,
                    ToolRegistry.getExecutionAffinity(toolName));
            assertOutcomeErrorCode("task17/mcp/default-affinity/outcome", outcome, "tool_execution_failed");
            assertEquals("task17/mcp/default-affinity/execute-count", 0, executeCount.get());
            assertTrue("task17/mcp/default-affinity/provider-thread-absent", providerThread.get() == null);
        } finally {
            ToolRegistry.unregister(providerId);
        }
    }

    private static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.chatmc.common.agent.AgentRunner$McSessionContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return constructor.newInstance(playerId);
        } catch (Exception exception) {
            throw new AssertionError("task17/mcp/new-session-context", exception);
        }
    }

    private static Object newToolCall(String toolName, String argsJson) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Constructor<?> constructor = toolCallClass.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task17/mcp/new-tool-call", exception);
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
            throw new AssertionError("task17/mcp/invoke-session-execute", exception);
        }
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

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task17/mcp/bootstrap", exception);
        }
    }

    private static String uniqueName(String prefix) {
        return prefix + "-task17-" + UUID.randomUUID();
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

        private ThreadCapturingToolProvider(
                String toolName,
                ToolProvider.ExecutionAffinity affinity,
                AtomicReference<Thread> providerThread,
                AtomicInteger executeCount
        ) {
            this.toolSpec = AgentToolSpec.metadataOnly(toolName, "", "{}", List.of(), "", List.of(), List.of());
            this.affinity = affinity;
            this.providerThread = providerThread;
            this.executeCount = executeCount;
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
            return ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
        }
    }
}
