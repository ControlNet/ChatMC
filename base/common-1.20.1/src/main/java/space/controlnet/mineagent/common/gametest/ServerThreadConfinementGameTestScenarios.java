package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolPayload;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class ServerThreadConfinementGameTestScenarios {
    private static final UUID THREAD_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID TIMEOUT_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final String THREAD_PLAYER_NAME = "thread_confine";
    private static final String TIMEOUT_PLAYER_NAME = "timeout_confine";
    private static final long FORCED_TIMEOUT_DELAY_MS = 31_000L;
    private static final long FORCED_FAILURE_DELAY_MS = 200L;
    private static final int TIMEOUT_WORKER_START_DELAY_TICKS = 40;
    private static final int TIMEOUT_ASSERT_DELAY_TICKS = 720;

    private ServerThreadConfinementGameTestScenarios() {
    }

    public static void asyncToolInvocationMarshalsToServerThread(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task9/server-thread/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer player = playerFactory.create(helper, THREAD_PLAYER_ID, THREAD_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task9/server-thread/setup/player", server, player);

        String providerId = uniqueName("task9-thread-provider");
        String toolName = uniqueName("task9-thread-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.IMMEDIATE_SUCCESS);
        ToolRegistry.register(providerId, provider);

        try {
            Object sessionContext = AgentGameTestSupport.newMcSessionContext(player.getUUID());
            Thread expectedServerThread = Thread.currentThread();

            AtomicReference<ToolOutcome> outcomeRef = new AtomicReference<>();
            AtomicReference<Throwable> workerErrorRef = new AtomicReference<>();

            Thread worker = new Thread(
                    () -> invokeExecuteTool(sessionContext, toolName, outcomeRef, workerErrorRef),
                    uniqueName("task9-thread-worker")
            );
            worker.start();

            helper.runAfterDelay(20, () -> {
                try {
                    AgentGameTestSupport.requireTrue("task9/server-thread/worker-completed", !worker.isAlive());
                    AgentGameTestSupport.requireNull("task9/server-thread/worker-error", workerErrorRef.get());
                    AgentGameTestSupport.requireEquals("task9/server-thread/execute-count", 1, provider.executeCount());

                    Thread observedProviderThread = AgentGameTestSupport.requireNonNull(
                            "task9/server-thread/provider-thread",
                            provider.firstExecutionThread()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task9/server-thread/provider-thread-is-server-thread",
                            expectedServerThread,
                            observedProviderThread
                    );
                    AgentGameTestSupport.requireTrue(
                            "task9/server-thread/provider-thread-not-worker",
                            observedProviderThread != worker
                    );

                    assertOutcomeSuccess("task9/server-thread/outcome", outcomeRef.get());
                    helper.succeed();
                } finally {
                    ToolRegistry.unregister(providerId);
                    AgentGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            ToolRegistry.unregister(providerId);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    public static void timeoutAndFailureContractsRemainStableUnderForcedDelay(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task9/timeout/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer player = playerFactory.create(helper, TIMEOUT_PLAYER_ID, TIMEOUT_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task9/timeout/setup/player", server, player);

        String providerId = uniqueName("task9-timeout-provider");
        String toolName = uniqueName("task9-timeout-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.FORCED_TIMEOUT_DELAY);
        ToolRegistry.register(providerId, provider);

        try {
            Object sessionContext = AgentGameTestSupport.newMcSessionContext(player.getUUID());
            Thread expectedServerThread = Thread.currentThread();

            AtomicReference<ToolOutcome> timeoutOutcomeRef = new AtomicReference<>();
            AtomicReference<Throwable> timeoutErrorRef = new AtomicReference<>();
            AtomicLong timeoutElapsedMs = new AtomicLong(-1L);

            Thread timeoutWorker = new Thread(
                    () -> invokeExecuteToolWithTiming(
                            sessionContext,
                            toolName,
                            timeoutOutcomeRef,
                            timeoutErrorRef,
                            timeoutElapsedMs
                    ),
                    uniqueName("task9-timeout-worker")
            );
            helper.runAfterDelay(TIMEOUT_WORKER_START_DELAY_TICKS, timeoutWorker::start);

            AtomicReference<ToolOutcome> failureOutcomeRef = new AtomicReference<>();
            AtomicReference<Throwable> failureErrorRef = new AtomicReference<>();
            AtomicLong failureElapsedMs = new AtomicLong(-1L);
            AtomicReference<Thread> failureWorkerRef = new AtomicReference<>();

            helper.runAfterDelay(TIMEOUT_ASSERT_DELAY_TICKS, () -> {
                try {
                    AgentGameTestSupport.requireTrue("task9/timeout/worker-completed", !timeoutWorker.isAlive());
                    AgentGameTestSupport.requireNull("task9/timeout/worker-error", timeoutErrorRef.get());

                    assertOutcomeError(
                            "task9/timeout/outcome-contract",
                            timeoutOutcomeRef.get(),
                            "tool_timeout",
                            "tool execution timeout"
                    );
                    AgentGameTestSupport.requireTrue(
                            "task9/timeout/elapsed-at-least-29s",
                            timeoutElapsedMs.get() >= 29_000L
                    );
                    AgentGameTestSupport.requireEquals(
                            "task9/timeout/provider-thread-is-server-thread",
                            expectedServerThread,
                            AgentGameTestSupport.requireNonNull(
                                    "task9/timeout/provider-first-thread",
                                    provider.firstExecutionThread()
                            )
                    );

                    provider.setMode(ToolExecutionMode.DELAYED_FAILURE);

                    Thread failureWorker = new Thread(
                            () -> invokeExecuteToolWithTiming(
                                    sessionContext,
                                    toolName,
                                    failureOutcomeRef,
                                    failureErrorRef,
                                    failureElapsedMs
                            ),
                            uniqueName("task9-failure-worker")
                    );
                    failureWorkerRef.set(failureWorker);
                    failureWorker.start();

                    helper.runAfterDelay(20, () -> {
                        try {
                            Thread currentFailureWorker = AgentGameTestSupport.requireNonNull(
                                    "task9/failure/worker-created",
                                    failureWorkerRef.get()
                            );
                            AgentGameTestSupport.requireTrue("task9/failure/worker-completed", !currentFailureWorker.isAlive());
                            AgentGameTestSupport.requireNull("task9/failure/worker-error", failureErrorRef.get());

                            assertOutcomeError(
                                    "task9/failure/outcome-contract",
                                    failureOutcomeRef.get(),
                                    "tool_execution_failed",
                                    "tool execution failed"
                            );
                            AgentGameTestSupport.requireTrue(
                                    "task9/failure/elapsed-honors-forced-delay",
                                    failureElapsedMs.get() >= FORCED_FAILURE_DELAY_MS
                            );
                            AgentGameTestSupport.requireEquals("task9/failure/execute-count", 2, provider.executeCount());
                            AgentGameTestSupport.requireEquals(
                                    "task9/failure/provider-thread-is-server-thread",
                                    expectedServerThread,
                                    AgentGameTestSupport.requireNonNull(
                                            "task9/failure/provider-last-thread",
                                            provider.lastExecutionThread()
                                    )
                            );

                            helper.succeed();
                        } finally {
                            ToolRegistry.unregister(providerId);
                            AgentGameTestSupport.resetRuntime();
                        }
                    });
                } catch (Throwable throwable) {
                    ToolRegistry.unregister(providerId);
                    AgentGameTestSupport.resetRuntime();
                    throw throwable;
                }
            });
        } catch (Throwable throwable) {
            ToolRegistry.unregister(providerId);
            AgentGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void invokeExecuteTool(
            Object sessionContext,
            String toolName,
            AtomicReference<ToolOutcome> outcomeRef,
            AtomicReference<Throwable> errorRef
    ) {
        try {
            outcomeRef.set(AgentGameTestSupport.invokeMcSessionExecuteTool(sessionContext, new ToolCall(toolName, "{}"), true));
        } catch (Throwable throwable) {
            errorRef.set(AgentGameTestSupport.rootCause(throwable));
        }
    }

    private static void invokeExecuteToolWithTiming(
            Object sessionContext,
            String toolName,
            AtomicReference<ToolOutcome> outcomeRef,
            AtomicReference<Throwable> errorRef,
            AtomicLong elapsedMsRef
    ) {
        long startedAt = System.nanoTime();
        try {
            outcomeRef.set(AgentGameTestSupport.invokeMcSessionExecuteTool(sessionContext, new ToolCall(toolName, "{}"), true));
        } catch (Throwable throwable) {
            errorRef.set(AgentGameTestSupport.rootCause(throwable));
        } finally {
            elapsedMsRef.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
    }

    private static void assertOutcomeSuccess(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = AgentGameTestSupport.requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = AgentGameTestSupport.requireNonNull(assertionName + "/result", nonNullOutcome.result());
        AgentGameTestSupport.requireTrue(assertionName + "/success", result.success());
    }

    private static void assertOutcomeError(
            String assertionName,
            ToolOutcome outcome,
            String expectedCode,
            String expectedMessage
    ) {
        ToolOutcome nonNullOutcome = AgentGameTestSupport.requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = AgentGameTestSupport.requireNonNull(assertionName + "/result", nonNullOutcome.result());
        AgentGameTestSupport.requireTrue(assertionName + "/must-be-failure", !result.success());
        AgentGameTestSupport.requireEquals(
                assertionName + "/error-code",
                expectedCode,
                AgentGameTestSupport.requireNonNull(assertionName + "/error", result.error()).code()
        );
        AgentGameTestSupport.requireEquals(
                assertionName + "/error-message",
                expectedMessage,
                AgentGameTestSupport.requireNonNull(assertionName + "/error-message-present", result.error()).message()
        );
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private enum ToolExecutionMode {
        IMMEDIATE_SUCCESS,
        FORCED_TIMEOUT_DELAY,
        DELAYED_FAILURE
    }

    private static final class ControlledToolProvider implements ToolProvider {
        private final AgentTool toolSpec;
        private final AtomicReference<ToolExecutionMode> mode;
        private final AtomicInteger executeCount = new AtomicInteger();
        private final AtomicReference<Thread> firstExecutionThread = new AtomicReference<>();
        private final AtomicReference<Thread> lastExecutionThread = new AtomicReference<>();

        private ControlledToolProvider(String toolName, ToolExecutionMode initialMode) {
            this.toolSpec = new StaticAgentTool(toolName);
            this.mode = new AtomicReference<>(initialMode);
        }

        private int executeCount() {
            return executeCount.get();
        }

        private Thread firstExecutionThread() {
            return firstExecutionThread.get();
        }

        private Thread lastExecutionThread() {
            return lastExecutionThread.get();
        }

        private void setMode(ToolExecutionMode nextMode) {
            mode.set(nextMode);
        }

        @Override
        public List<AgentTool> specs() {
            return List.of(toolSpec);
        }

        @Override
        public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            executeCount.incrementAndGet();
            Thread currentThread = Thread.currentThread();
            firstExecutionThread.compareAndSet(null, currentThread);
            lastExecutionThread.set(currentThread);

            return switch (mode.get()) {
                case IMMEDIATE_SUCCESS -> ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
                case FORCED_TIMEOUT_DELAY -> {
                    blockAtLeast(FORCED_TIMEOUT_DELAY_MS);
                    yield ToolOutcome.result(ToolResult.ok("{\"delayed\":\"timeout\"}"));
                }
                case DELAYED_FAILURE -> {
                    blockAtLeast(FORCED_FAILURE_DELAY_MS);
                    throw new IllegalStateException("task9/forced-delay/failure");
                }
            };
        }

        private static void blockAtLeast(long millis) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    return;
                }
                LockSupport.parkNanos(remaining);
            }
        }
    }

    private static final class StaticAgentTool implements AgentTool {
        private final String name;

        private StaticAgentTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Task9 controlled test tool";
        }

        @Override
        public String argsSchema() {
            return "{}";
        }

        @Override
        public List<String> argsDescription() {
            return List.of();
        }

        @Override
        public String resultSchema() {
            return "{}";
        }

        @Override
        public List<String> resultDescription() {
            return List.of();
        }

        @Override
        public List<String> examples() {
            return List.of();
        }

        @Override
        public ToolRender render(ToolPayload payload) {
            return null;
        }
    }
}
