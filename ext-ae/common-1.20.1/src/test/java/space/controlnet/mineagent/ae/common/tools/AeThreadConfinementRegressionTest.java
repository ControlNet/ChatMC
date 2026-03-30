package space.controlnet.mineagent.ae.common.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.ae.core.terminal.AeTerminalContext;
import space.controlnet.mineagent.ae.core.terminal.AiTerminalData;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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

    @Test
    void task14_aeProvider_unapprovedRequestCraft_returnsProposal() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome outcome = provider.execute(Optional.empty(),
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":2}"), false);

        assertNull("task14/ae/request-craft/proposal-result", outcome.result());
        assertEquals("task14/ae/request-craft/proposal-summary", "Craft 2 ae2:controller",
                requireNonNull("task14/ae/request-craft/proposal", outcome.proposal()).summary());
    }

    @Test
    void task14_aeProvider_unapprovedJobCancel_returnsProposal() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome outcome = provider.execute(Optional.empty(),
                new ToolCall("ae.job_cancel", "{\"jobId\":\"job-42\"}"), false);

        assertNull("task14/ae/job-cancel/proposal-result", outcome.result());
        assertEquals("task14/ae/job-cancel/proposal-summary", "Cancel job job-42",
                requireNonNull("task14/ae/job-cancel/proposal", outcome.proposal()).summary());
    }

    @Test
    void task14_aeProvider_malformedAndMissingIdentifiers_returnInvalidArgs() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome malformed = provider.execute(Optional.empty(),
                new ToolCall("ae.simulate_craft", "{bad-json"), true);
        ToolOutcome missingItemId = provider.execute(Optional.empty(),
                new ToolCall("ae.request_craft", "{\"count\":1}"), true);
        ToolOutcome missingJobId = provider.execute(Optional.empty(),
                new ToolCall("ae.job_status", "{\"jobId\":\"\"}"), true);

        assertErrorCode("task14/ae/malformed-json", malformed, "invalid_args");
        assertErrorCode("task14/ae/missing-item-id", missingItemId, "invalid_args");
        assertErrorCode("task14/ae/missing-job-id", missingJobId, "invalid_args");
    }

    @Test
    void task14_aeProvider_routesMainToolFamiliesToTerminalDouble() {
        AeToolProvider provider = new AeToolProvider();
        RecordingAeTerminalContext terminal = new RecordingAeTerminalContext();

        ToolResult listItems = requireResult("task14/ae/routes/list-items", provider.execute(Optional.of(terminal),
                new ToolCall("ae.list_items", "{\"query\":\"fluix\",\"craftableOnly\":false,\"limit\":3}"), true));
        ToolResult listCraftables = requireResult("task14/ae/routes/list-craftables", provider.execute(Optional.of(terminal),
                new ToolCall("ae.list_craftables", "{\"query\":\"processor\",\"limit\":2}"), true));
        ToolResult simulate = requireResult("task14/ae/routes/simulate", provider.execute(Optional.of(terminal),
                new ToolCall("ae.simulate_craft", "{\"itemId\":\"ae2:controller\",\"count\":1}"), true));
        ToolResult request = requireResult("task14/ae/routes/request", provider.execute(Optional.of(terminal),
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":2,\"cpuName\":\"cpu-a\"}"), true));
        ToolResult status = requireResult("task14/ae/routes/status", provider.execute(Optional.of(terminal),
                new ToolCall("ae.job_status", "{\"jobId\":\"job-9\"}"), true));
        ToolResult cancel = requireResult("task14/ae/routes/cancel", provider.execute(Optional.of(terminal),
                new ToolCall("ae.job_cancel", "{\"jobId\":\"job-9\"}"), true));

        assertTrue("task14/ae/routes/list-items-success", listItems.success());
        assertTrue("task14/ae/routes/list-craftables-success", listCraftables.success());
        assertTrue("task14/ae/routes/simulate-success", simulate.success());
        assertTrue("task14/ae/routes/request-success", request.success());
        assertTrue("task14/ae/routes/status-success", status.success());
        assertTrue("task14/ae/routes/cancel-success", cancel.success());
        assertContains("task14/ae/routes/list-items-payload", listItems.payloadJson(), "ae2:fluix_crystal");
        assertContains("task14/ae/routes/request-payload", request.payloadJson(), "queued");
        assertContains("task14/ae/routes/status-payload", status.payloadJson(), "job-9");
        assertEquals("task14/ae/routes/invocations", List.of(
                "listItems:fluix:false:3:null",
                "listCraftables:processor:2:null",
                "simulateCraft:ae2:controller:1",
                "requestCraft:ae2:controller:2:cpu-a",
                "jobStatus:job-9",
                "cancelJob:job-9"
        ), terminal.invocations());
    }

    private static Object invokeAeProviderExecute(AeToolProvider provider, String toolName, String argsJson, boolean approved) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.mineagent.core.tools.ToolCall");
            Method execute = AeToolProvider.class.getMethod("execute", Optional.class, toolCallClass, boolean.class);
            return execute.invoke(provider, Optional.empty(), newToolCall(toolName, argsJson), approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/invoke-provider-execute", exception);
        }
    }

    private static Object invokeToolRegistryExecuteTool(String toolName, String argsJson, boolean approved) {
        try {
            Class<?> toolRegistryClass = Class.forName("space.controlnet.mineagent.common.tools.ToolRegistry");
            Class<?> toolCallClass = Class.forName("space.controlnet.mineagent.core.tools.ToolCall");
            Method execute = toolRegistryClass.getMethod("executeTool", Optional.class, toolCallClass, boolean.class);
            return execute.invoke(null, Optional.empty(), newToolCall(toolName, argsJson), approved);
        } catch (Exception exception) {
            throw new AssertionError("task14/ae/invoke-registry-execute", exception);
        }
    }

    private static Object newToolCall(String toolName, String argsJson) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.mineagent.core.tools.ToolCall");
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
            Class<?> toolProviderClass = Class.forName("space.controlnet.mineagent.common.tools.ToolProvider");
            Class<?> toolRegistryClass = Class.forName("space.controlnet.mineagent.common.tools.ToolRegistry");

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

    private static ToolResult requireResult(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        return requireNonNull(assertionName + "/result", nonNullOutcome.result());
    }

    private static void assertErrorCode(String assertionName, ToolOutcome outcome, String expectedCode) {
        ToolResult result = requireResult(assertionName, outcome);
        assertTrue(assertionName + "/must-fail", !result.success());
        assertEquals(assertionName + "/code", expectedCode,
                requireNonNull(assertionName + "/error", result.error()).code());
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

    private static void assertNull(String assertionName, Object value) {
        if (value == null) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected null but was: " + value);
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
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

    private static final class RecordingAeTerminalContext implements AeTerminalContext {
        private final List<String> invocations = new ArrayList<>();

        @Override
        public AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, String pageToken) {
            invocations.add("listItems:" + query + ":" + craftableOnly + ":" + limit + ":" + pageToken);
            return new AiTerminalData.AeListResult(
                    List.of(new AiTerminalData.AeEntry("ae2:fluix_crystal", 12L, true)),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeListResult listCraftables(String query, int limit, String pageToken) {
            invocations.add("listCraftables:" + query + ":" + limit + ":" + pageToken);
            return new AiTerminalData.AeListResult(
                    List.of(new AiTerminalData.AeEntry("ae2:logic_processor", 0L, true)),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeCraftSimulation simulateCraft(String itemId, long count) {
            invocations.add("simulateCraft:" + itemId + ":" + count);
            return new AiTerminalData.AeCraftSimulation(
                    "sim-1",
                    "planned",
                    List.of(new AiTerminalData.AePlanItem("minecraft:redstone", 4L)),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeCraftRequest requestCraft(String itemId, long count, String cpuName) {
            invocations.add("requestCraft:" + itemId + ":" + count + ":" + cpuName);
            return new AiTerminalData.AeCraftRequest("job-9", "queued", Optional.empty());
        }

        @Override
        public AiTerminalData.AeJobStatus jobStatus(String jobId) {
            invocations.add("jobStatus:" + jobId);
            return new AiTerminalData.AeJobStatus(
                    jobId,
                    "running",
                    List.of(new AiTerminalData.AePlanItem("minecraft:quartz", 2L)),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeJobStatus cancelJob(String jobId) {
            invocations.add("cancelJob:" + jobId);
            return new AiTerminalData.AeJobStatus(jobId, "cancelled", List.of(), Optional.empty());
        }

        private List<String> invocations() {
            return List.copyOf(invocations);
        }
    }

}
