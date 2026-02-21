package space.controlnet.chatmc.common.agent;

import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.tools.ToolRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

        registerThreadCapturingProvider(providerId, toolName, providerThread, executeCount);

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
        joinOrFail("task14/base/registry-dispatch/join", worker);
        if (errorRef.get() != null) {
            throw new AssertionError("task14/base/registry-dispatch/unexpected-error", errorRef.get());
        }

        assertEquals("task14/base/registry-dispatch/execute-count", 1, executeCount.get());
        assertEquals("task14/base/registry-dispatch/provider-thread", worker.getName(),
                requireNonNull("task14/base/registry-dispatch/provider-thread-present", providerThread.get()).getName());
        assertOutcomeSuccess("task14/base/registry-dispatch/outcome", outcomeRef.get());
    }

    @Test
    void task14_timeoutFailureContract_presentInAgentRunnerSource() {
        String source = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java");

        assertContains("task14/base/timeout-contract/timeout-constant", source,
                "TOOL_EXECUTION_TIMEOUT_MS = 30_000L");
        assertContains("task14/base/timeout-contract/timeout-branch", source,
                "ToolResult.error(\"tool_timeout\", \"tool execution timeout\")");
        assertContains("task14/base/timeout-contract/failure-branch", source,
                "ToolResult.error(\"tool_execution_failed\", \"tool execution failed\")");
    }

    private static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.chatmc.common.agent.AgentRunner$McSessionContext");
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
            AtomicInteger executeCount
    ) {
        try {
            Class<?> toolProviderClass = Class.forName("space.controlnet.chatmc.common.tools.ToolProvider");
            Class<?> agentToolClass = Class.forName("space.controlnet.chatmc.core.tools.AgentTool");

            Object toolSpec = Proxy.newProxyInstance(
                    agentToolClass.getClassLoader(),
                    new Class<?>[]{agentToolClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "name" -> toolName;
                        case "description", "argsSchema", "resultSchema" -> "";
                        case "argsDescription", "resultDescription", "examples" -> List.of();
                        case "render" -> null;
                        case "toString" -> "Task14ToolSpec(" + toolName + ")";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("Unexpected AgentTool method: " + method.getName());
                    }
            );

            InvocationHandler providerHandler = (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "specs" -> List.of(toolSpec);
                    case "execute" -> {
                        executeCount.incrementAndGet();
                        providerThread.set(Thread.currentThread());
                        yield newToolOutcomeResult(newToolResultOk("{\"ok\":true}"));
                    }
                    case "toString" -> "Task14ThreadCapturingProvider(" + providerId + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Unexpected ToolProvider method: " + method.getName());
                };
            };

            Object providerProxy = Proxy.newProxyInstance(
                    toolProviderClass.getClassLoader(),
                    new Class<?>[]{toolProviderClass},
                    providerHandler
            );

            Method registerMethod = ToolRegistry.class.getMethod("register", String.class, toolProviderClass);
            registerMethod.invoke(null, providerId, providerProxy);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/register-provider", exception);
        }
    }

    private static Object newToolResultOk(String payloadJson) {
        try {
            Class<?> toolResultClass = Class.forName("space.controlnet.chatmc.core.tools.ToolResult");
            Method okMethod = toolResultClass.getMethod("ok", String.class);
            return okMethod.invoke(null, payloadJson);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/new-tool-result-ok", exception);
        }
    }

    private static Object newToolOutcomeResult(Object result) {
        try {
            Class<?> toolResultClass = Class.forName("space.controlnet.chatmc.core.tools.ToolResult");
            Class<?> toolOutcomeClass = Class.forName("space.controlnet.chatmc.core.tools.ToolOutcome");
            Method resultMethod = toolOutcomeClass.getMethod("result", toolResultClass);
            return resultMethod.invoke(null, result);
        } catch (Exception exception) {
            throw new AssertionError("task14/base/new-tool-outcome-result", exception);
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

    private static void joinOrFail(String assertionName, Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError(assertionName + " -> interrupted", interruptedException);
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
}
