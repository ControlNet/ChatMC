package space.controlnet.mineagent.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.core.session.PersistedSessions;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolPayload;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagent")
public final class ServerThreadConfinementGameTest {
    private static final UUID THREAD_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID TIMEOUT_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final String THREAD_PLAYER_NAME = "thread_confine";
    private static final String TIMEOUT_PLAYER_NAME = "timeout_confine";
    private static final long FORCED_TIMEOUT_DELAY_MS = 31_000L;
    private static final long FORCED_FAILURE_DELAY_MS = 200L;
    private static final int TIMEOUT_WORKER_START_DELAY_TICKS = 40;
    private static final int TIMEOUT_ASSERT_DELAY_TICKS = 720;

    private ServerThreadConfinementGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent")
    public static void asyncToolInvocationMarshalsToServerThread(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> asyncToolInvocationMarshalsToServerThreadInternal(helper));
    }

    private static void asyncToolInvocationMarshalsToServerThreadInternal(GameTestHelper helper) {
        resetSharedNetworkState(false);

        MinecraftServer server = requireNonNull("task9/server-thread/setup/server", helper.getLevel().getServer());
        MineAgentNetwork.setServer(server);

        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(THREAD_PLAYER_ID, THREAD_PLAYER_NAME)
        );
        ensurePlayerResolvableByChatNetwork("task9/server-thread/setup/player", server, player);

        String toolName = uniqueName("task9-thread-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.IMMEDIATE_SUCCESS);
        ToolRegistry.register(uniqueName("task9-thread-provider"), provider);

        Object sessionContext = newMcSessionContext(player.getUUID());
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
                requireTrue("task9/server-thread/worker-completed", !worker.isAlive());
                requireNull("task9/server-thread/worker-error", workerErrorRef.get());
                requireEquals("task9/server-thread/execute-count", 1, provider.executeCount());

                Thread observedProviderThread = requireNonNull(
                        "task9/server-thread/provider-thread",
                        provider.firstExecutionThread()
                );
                requireEquals(
                        "task9/server-thread/provider-thread-is-server-thread",
                        expectedServerThread,
                        observedProviderThread
                );
                requireTrue(
                        "task9/server-thread/provider-thread-not-worker",
                        observedProviderThread != worker
                );

                assertOutcomeSuccess("task9/server-thread/outcome", outcomeRef.get());
                helper.succeed();
            } finally {
                resetSharedNetworkState(true);
            }
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent_task9_timeout", timeoutTicks = 2400)
    public static void timeoutAndFailureContractsRemainStableUnderForcedDelay(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> timeoutAndFailureContractsRemainStableUnderForcedDelayInternal(helper));
    }

    private static void timeoutAndFailureContractsRemainStableUnderForcedDelayInternal(GameTestHelper helper) {
        resetSharedNetworkState(false);

        MinecraftServer server = requireNonNull("task9/timeout/setup/server", helper.getLevel().getServer());
        MineAgentNetwork.setServer(server);

        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(TIMEOUT_PLAYER_ID, TIMEOUT_PLAYER_NAME)
        );
        ensurePlayerResolvableByChatNetwork("task9/timeout/setup/player", server, player);

        String toolName = uniqueName("task9-timeout-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.FORCED_TIMEOUT_DELAY);
        ToolRegistry.register(uniqueName("task9-timeout-provider"), provider);

        Object sessionContext = newMcSessionContext(player.getUUID());
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
                requireTrue("task9/timeout/worker-completed", !timeoutWorker.isAlive());
                requireNull("task9/timeout/worker-error", timeoutErrorRef.get());

                assertOutcomeError(
                        "task9/timeout/outcome-contract",
                        timeoutOutcomeRef.get(),
                        "tool_timeout",
                        "tool execution timeout"
                );
                requireTrue(
                        "task9/timeout/elapsed-at-least-29s",
                        timeoutElapsedMs.get() >= 29_000L
                );
                requireEquals(
                        "task9/timeout/provider-thread-is-server-thread",
                        expectedServerThread,
                        requireNonNull("task9/timeout/provider-first-thread", provider.firstExecutionThread())
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
                        Thread currentFailureWorker = requireNonNull(
                                "task9/failure/worker-created",
                                failureWorkerRef.get()
                        );
                        requireTrue("task9/failure/worker-completed", !currentFailureWorker.isAlive());
                        requireNull("task9/failure/worker-error", failureErrorRef.get());

                        assertOutcomeError(
                                "task9/failure/outcome-contract",
                                failureOutcomeRef.get(),
                                "tool_execution_failed",
                                "tool execution failed"
                        );
                        requireTrue(
                                "task9/failure/elapsed-honors-forced-delay",
                                failureElapsedMs.get() >= FORCED_FAILURE_DELAY_MS
                        );
                        requireEquals("task9/failure/execute-count", 2, provider.executeCount());
                        requireEquals(
                                "task9/failure/provider-thread-is-server-thread",
                                expectedServerThread,
                                requireNonNull("task9/failure/provider-last-thread", provider.lastExecutionThread())
                        );

                        helper.succeed();
                    } finally {
                        resetSharedNetworkState(true);
                    }
                });
            } catch (Throwable throwable) {
                resetSharedNetworkState(true);
                throw throwable;
            }
        });
    }

    private static void invokeExecuteTool(
            Object sessionContext,
            String toolName,
            AtomicReference<ToolOutcome> outcomeRef,
            AtomicReference<Throwable> errorRef
    ) {
        try {
            outcomeRef.set(invokeMcSessionExecuteTool(sessionContext, new ToolCall(toolName, "{}"), true));
        } catch (Throwable throwable) {
            errorRef.set(rootCause(throwable));
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
            outcomeRef.set(invokeMcSessionExecuteTool(sessionContext, new ToolCall(toolName, "{}"), true));
        } catch (Throwable throwable) {
            errorRef.set(rootCause(throwable));
        } finally {
            elapsedMsRef.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
    }

    private static void ensurePlayerResolvableByChatNetwork(
            String assertionPrefix,
            MinecraftServer server,
            ServerPlayer player
    ) {
        UUID playerId = requireNonNull(assertionPrefix + "/id", player.getUUID());
        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
            return;
        }

        Object playerList = requireNonNull(assertionPrefix + "/player-list", server.getPlayerList());
        requireTrue(
                assertionPrefix + "/inject-player-lookup",
                injectPlayerLookup(playerList, playerId, player)
        );
        requireTrue(assertionPrefix + "/lookup-present", MineAgentNetwork.findPlayer(playerId).isPresent());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean injectPlayerLookup(Object playerList, UUID playerId, ServerPlayer player) {
        Class<?> currentClass = playerList.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                field.setAccessible(true);

                try {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        Map map = (Map) field.get(playerList);
                        if (map == null) {
                            continue;
                        }

                        Object previous = map.put(playerId, player);
                        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (previous == null) {
                            map.remove(playerId);
                        } else {
                            map.put(playerId, previous);
                        }
                    } else if (List.class.isAssignableFrom(field.getType())) {
                        List list = (List) field.get(playerList);
                        if (list == null) {
                            continue;
                        }

                        boolean added = false;
                        if (!list.contains(player)) {
                            list.add(player);
                            added = true;
                        }

                        if (MineAgentNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (added) {
                            list.remove(player);
                        }
                    }
                } catch (IllegalAccessException | UnsupportedOperationException | ClassCastException ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    private static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.mineagent.common.agent.McSessionContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return constructor.newInstance(playerId);
        } catch (Exception exception) {
            throw new AssertionError("task9/reflection/new-session-context", exception);
        }
    }

    private static ToolOutcome invokeMcSessionExecuteTool(Object sessionContext, ToolCall call, boolean approved) {
        try {
            Method executeMethod = sessionContext.getClass().getDeclaredMethod(
                    "executeTool",
                    Optional.class,
                    ToolCall.class,
                    boolean.class
            );
            executeMethod.setAccessible(true);
            return (ToolOutcome) executeMethod.invoke(sessionContext, Optional.empty(), call, approved);
        } catch (Exception exception) {
            throw new AssertionError("task9/reflection/invoke-mc-session-execute-tool", rootCause(exception));
        }
    }

    private static void resetSharedNetworkState(boolean releaseLease) {
        MineAgentNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        if (releaseLease) {
            GameTestRuntimeLease.release();
        }
    }

    private static void clearSessionLocale() {
        try {
            Field field = MineAgentNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task9/cleanup/clear-session-locale", exception);
        }
    }

    private static void assertOutcomeSuccess(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = requireNonNull(assertionName + "/result", nonNullOutcome.result());
        requireTrue(assertionName + "/success", result.success());
    }

    private static void assertOutcomeError(
            String assertionName,
            ToolOutcome outcome,
            String expectedCode,
            String expectedMessage
    ) {
        ToolOutcome nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = requireNonNull(assertionName + "/result", nonNullOutcome.result());
        requireTrue(assertionName + "/must-be-failure", !result.success());
        requireEquals(assertionName + "/error-code", expectedCode,
                requireNonNull(assertionName + "/error", result.error()).code());
        requireEquals(assertionName + "/error-message", expectedMessage,
                requireNonNull(assertionName + "/error-message-present", result.error()).message());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static void requireNull(String assertionName, Object value) {
        if (value == null) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected null, actual: " + value);
    }

    private static void requireTrue(String assertionName, boolean condition) {
        if (!condition) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
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

            ToolExecutionMode currentMode = mode.get();
            return switch (currentMode) {
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
