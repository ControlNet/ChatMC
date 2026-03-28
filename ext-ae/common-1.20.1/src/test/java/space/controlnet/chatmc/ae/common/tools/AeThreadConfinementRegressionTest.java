package space.controlnet.chatmc.ae.common.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AeThreadConfinementRegressionTest {
    @Test
    void task14_aeProvider_listItemsWithoutTerminal_returnsNoTerminal() {
        AeToolProvider provider = new AeToolProvider();
        Object outcome = invokeAeProviderExecute(provider,
                "ae.list_items", "{\"query\":\"\",\"craftableOnly\":false,\"limit\":5}", true);

        assertOutcomeErrorCode("task14/ae/list-items/no-terminal", outcome, "no_terminal");
    }

    @Test
    void task14_aeProvider_invalidCraftCount_isDeterministicFailure() {
        AeToolProvider provider = new AeToolProvider();
        String invalidArgs = "{\"itemId\":\"ae2:controller\",\"count\":0}";

        Object first = invokeAeProviderExecute(provider, "ae.simulate_craft", invalidArgs, true);
        Object second = invokeAeProviderExecute(provider, "ae.simulate_craft", invalidArgs, true);

        assertOutcomeErrorCode("task14/ae/invalid-count/first", first, "invalid_count");
        assertOutcomeErrorCode("task14/ae/invalid-count/second", second, "invalid_count");
    }

    @Test
    void task14_aeRegistryDispatch_executesProviderOnCallingThread() {
        String providerId = uniqueName("provider");
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        AtomicInteger executeCount = new AtomicInteger();
        registerThreadCapturingProvider(providerId, providerThread, executeCount);

        AtomicReference<Object> outcomeRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                outcomeRef.set(invokeToolRegistryExecuteTool("ae.list_craftables", "{\"query\":\"\",\"limit\":3}",
                        true));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            }
        }, uniqueName("worker"));

        worker.start();
        joinOrFail("task14/ae/registry-dispatch/thread-join", worker);
        if (errorRef.get() != null) {
            throw new AssertionError("task14/ae/registry-dispatch/unexpected-error", errorRef.get());
        }

        assertEquals("task14/ae/registry-dispatch/execute-count", 1, executeCount.get());
        assertEquals("task14/ae/registry-dispatch/provider-thread", worker.getName(),
                requireNonNull("task14/ae/registry-dispatch/provider-thread-present", providerThread.get()).getName());
        assertOutcomeErrorCode("task14/ae/registry-dispatch/outcome", outcomeRef.get(), "no_terminal");
    }

    private static Object invokeAeProviderExecute(AeToolProvider provider, String toolName, String argsJson, boolean approved) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Method execute = AeToolProvider.class.getMethod("execute", Optional.class, toolCallClass, boolean.class);
            return execute.invoke(provider, Optional.empty(), newToolCall(toolName, argsJson), approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/invoke-provider-execute", exception);
        }
    }

    private static Object invokeToolRegistryExecuteTool(String toolName, String argsJson, boolean approved) {
        try {
            Class<?> toolRegistryClass = Class.forName("space.controlnet.chatmc.common.tools.ToolRegistry");
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            Method execute = toolRegistryClass.getMethod("executeTool", Optional.class, toolCallClass, boolean.class);
            return execute.invoke(null, Optional.empty(), newToolCall(toolName, argsJson), approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/invoke-registry-execute", exception);
        }
    }

    private static Object newToolCall(String toolName, String argsJson) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.chatmc.core.tools.ToolCall");
            return toolCallClass.getDeclaredConstructor(String.class, String.class).newInstance(toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/new-tool-call", exception);
        }
    }

    private static void registerThreadCapturingProvider(
            String providerId,
            AtomicReference<Thread> providerThread,
            AtomicInteger executeCount
    ) {
        try {
            AeToolProvider delegate = new AeToolProvider();
            Class<?> toolProviderClass = Class.forName("space.controlnet.chatmc.common.tools.ToolProvider");
            Class<?> toolRegistryClass = Class.forName("space.controlnet.chatmc.common.tools.ToolRegistry");

            InvocationHandler handler = (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "specs" -> method.invoke(delegate);
                    case "execute" -> {
                        executeCount.incrementAndGet();
                        providerThread.set(Thread.currentThread());
                        yield method.invoke(delegate, args);
                    }
                    case "toString" -> "Task14AeThreadCapturingProvider(" + providerId + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Unexpected ToolProvider method: " + method.getName());
                };
            };

            Object proxy = Proxy.newProxyInstance(
                    toolProviderClass.getClassLoader(),
                    new Class<?>[]{toolProviderClass},
                    handler
            );

            Method register = toolRegistryClass.getMethod("register", String.class, toolProviderClass);
            register.invoke(null, providerId, proxy);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/register-provider", exception);
        }
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
