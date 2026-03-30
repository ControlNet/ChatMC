package space.controlnet.mineagent.ae.forge.gametest;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.ae.common.part.AiTerminalPartOperations;
import space.controlnet.mineagent.ae.common.terminal.AeTerminalHost;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
public final class AeCraftLifecycleIsolationGameTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String PLAYER_NAME = "ae_lifecycle";
    private static final String ITEM_ID = "minecraft:stick";

    private AeCraftLifecycleIsolationGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagentae", timeoutTicks = 600)
    public static void craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(PLAYER_ID, PLAYER_NAME)
        );

        AeTerminalHost terminalHost = newTerminalHostProxy();

        AiTerminalPartOperations terminalA = new AiTerminalPartOperations();
        AiTerminalPartOperations terminalB = new AiTerminalPartOperations();

        ControlledCraftingService serviceA = new ControlledCraftingService("task12/terminal-a");
        ControlledCraftingService serviceB = new ControlledCraftingService("task12/terminal-b");

        IGrid gridA = newGridProxy(serviceA);
        IGrid gridB = newGridProxy(serviceB);

        ControlledLink linkA = new ControlledLink("task12/link-a");
        serviceA.enqueueSuccessfulCraft(linkA.link());

        CraftRequestView requestA = invokeRequestCraft(
                terminalA,
                player,
                gridA,
                helper.getLevel(),
                terminalHost,
                ITEM_ID,
                1L,
                null
        );

        requireEquals("task12/terminal-a/begin-status", "calculating", requestA.status());
        requireTrue("task12/terminal-a/begin-job-id", requestA.jobId() != null && !requestA.jobId().isBlank());
        requireTrue("task12/terminal-a/begin-error-empty", requestA.error().isEmpty());
        requireUnknownOnOtherTerminal("task12/terminal-a/no-cross-terminal-at-begin", terminalB, requestA.jobId());

        awaitStatus(
                helper,
                "task12/terminal-a/wait-submitted",
                terminalA,
                requestA.jobId(),
                "submitted",
                80,
                () -> {
                    requireUnknownOnOtherTerminal(
                            "task12/terminal-a/no-cross-terminal-while-submitted",
                            terminalB,
                            requestA.jobId()
                    );

                    linkA.markDone();
                    terminalA.jobStateChange(linkA.link());

                    awaitStatus(
                            helper,
                            "task12/terminal-a/wait-done",
                            terminalA,
                            requestA.jobId(),
                            "done",
                            40,
                            () -> runFailureAndRecoveryFlow(
                                    helper,
                                    player,
                                    terminalHost,
                                    gridB,
                                    terminalA,
                                    terminalB,
                                    serviceB,
                                    requestA.jobId()
                            )
                    );
                }
        );
    }

    private static void runFailureAndRecoveryFlow(
            GameTestHelper helper,
            ServerPlayer player,
            AeTerminalHost terminalHost,
            IGrid gridB,
            AiTerminalPartOperations terminalA,
            AiTerminalPartOperations terminalB,
            ControlledCraftingService serviceB,
            String terminalAJobId
    ) {
        serviceB.enqueueCalculationFailure("forced-calc-failure");

        CraftRequestView failedRequest = invokeRequestCraft(
                terminalB,
                player,
                gridB,
                helper.getLevel(),
                terminalHost,
                ITEM_ID,
                1L,
                null
        );

        requireEquals("task12/terminal-b/failure-begin-status", "calculating", failedRequest.status());
        requireTrue("task12/terminal-b/failure-begin-job-id", failedRequest.jobId() != null && !failedRequest.jobId().isBlank());

        awaitStatus(
                helper,
                "task12/terminal-b/wait-failed",
                terminalB,
                failedRequest.jobId(),
                "failed",
                80,
                () -> {
                    JobStatusView failedStatus = invokeJobStatus(terminalB, failedRequest.jobId());
                    requireTrue("task12/terminal-b/failure-error-present", failedStatus.error().isPresent());
                    requireContains(
                            "task12/terminal-b/failure-error-signal",
                            failedStatus.error().orElse(""),
                            "forced-calc-failure"
                    );

                    requireEquals(
                            "task12/terminal-a/remains-done-after-terminal-b-failure",
                            "done",
                            invokeJobStatus(terminalA, terminalAJobId).status()
                    );
                    requireUnknownOnOtherTerminal(
                            "task12/terminal-b/failure-no-cross-terminal-to-a",
                            terminalA,
                            failedRequest.jobId()
                    );

                    runPostFailureIsolationFlow(
                            helper,
                            player,
                            terminalHost,
                            gridB,
                            terminalA,
                            terminalB,
                            serviceB,
                            terminalAJobId,
                            failedRequest.jobId()
                    );
                }
        );
    }

    private static void runPostFailureIsolationFlow(
            GameTestHelper helper,
            ServerPlayer player,
            AeTerminalHost terminalHost,
            IGrid gridB,
            AiTerminalPartOperations terminalA,
            AiTerminalPartOperations terminalB,
            ControlledCraftingService serviceB,
            String terminalAJobId,
            String failedJobId
    ) {
        ControlledLink recoveredLink = new ControlledLink("task12/link-b-recovered");
        serviceB.enqueueSuccessfulCraft(recoveredLink.link());

        CraftRequestView recoveredRequest = invokeRequestCraft(
                terminalB,
                player,
                gridB,
                helper.getLevel(),
                terminalHost,
                ITEM_ID,
                1L,
                null
        );

        requireEquals("task12/terminal-b/recovery-begin-status", "calculating", recoveredRequest.status());
        requireTrue(
                "task12/terminal-b/recovery-uses-fresh-job-id",
                !recoveredRequest.jobId().equals(failedJobId)
        );
        requireTrue("task12/terminal-b/recovery-begin-error-empty", recoveredRequest.error().isEmpty());
        requireUnknownOnOtherTerminal(
                "task12/terminal-b/recovery-no-cross-terminal-to-a",
                terminalA,
                recoveredRequest.jobId()
        );

        awaitStatus(
                helper,
                "task12/terminal-b/recovery-wait-submitted",
                terminalB,
                recoveredRequest.jobId(),
                "submitted",
                80,
                () -> {
                    JobStatusView submittedStatus = invokeJobStatus(terminalB, recoveredRequest.jobId());
                    requireTrue("task12/terminal-b/recovery-submitted-error-empty", submittedStatus.error().isEmpty());

                    recoveredLink.markDone();
                    terminalB.jobStateChange(recoveredLink.link());

                    awaitStatus(
                            helper,
                            "task12/terminal-b/recovery-wait-done",
                            terminalB,
                            recoveredRequest.jobId(),
                            "done",
                            40,
                            () -> {
                                requireEquals(
                                        "task12/terminal-b/failed-job-remains-failed-after-recovery",
                                        "failed",
                                        invokeJobStatus(terminalB, failedJobId).status()
                                );
                                requireEquals(
                                        "task12/terminal-a/job-remains-done-after-terminal-b-recovery",
                                        "done",
                                        invokeJobStatus(terminalA, terminalAJobId).status()
                                );
                                requireUnknownOnOtherTerminal(
                                        "task12/final/no-cross-terminal-terminal-a-job-on-b",
                                        terminalB,
                                        terminalAJobId
                                );
                                requireUnknownOnOtherTerminal(
                                        "task12/final/no-cross-terminal-terminal-b-failed-job-on-a",
                                        terminalA,
                                        failedJobId
                                );
                                requireUnknownOnOtherTerminal(
                                        "task12/final/no-cross-terminal-terminal-b-recovery-job-on-a",
                                        terminalA,
                                        recoveredRequest.jobId()
                                );

                                helper.succeed();
                            }
                    );
                }
        );
    }

    private static void awaitStatus(
            GameTestHelper helper,
            String assertionName,
            AiTerminalPartOperations ops,
            String jobId,
            String expectedStatus,
            int ticksRemaining,
            Runnable onReady
    ) {
        JobStatusView status = invokeJobStatus(ops, jobId);
        if (expectedStatus.equals(status.status())) {
            onReady.run();
            return;
        }

        if (ticksRemaining <= 0) {
            throw new AssertionError(
                    assertionName
                            + " -> timed out waiting for status='"
                            + expectedStatus
                            + "', actual='"
                            + status.status()
                            + "', error="
                            + status.error().orElse("<none>")
            );
        }

        helper.runAfterDelay(
                1,
                () -> awaitStatus(helper, assertionName, ops, jobId, expectedStatus, ticksRemaining - 1, onReady)
        );
    }

    private static void requireUnknownOnOtherTerminal(
            String assertionPrefix,
            AiTerminalPartOperations otherTerminal,
            String foreignJobId
    ) {
        JobStatusView status = invokeJobStatus(otherTerminal, foreignJobId);
        requireEquals(assertionPrefix + "/status", "unknown", status.status());
        requireTrue(assertionPrefix + "/missing-job-error-present", status.error().isPresent());
        requireEquals(assertionPrefix + "/missing-job-error", "Job not found", status.error().orElseThrow());
    }

    private static CraftRequestView invokeRequestCraft(
            AiTerminalPartOperations ops,
            Player player,
            IGrid grid,
            Level level,
            AeTerminalHost host,
            String itemId,
            long count,
            String cpuName
    ) {
        try {
            Method method = AiTerminalPartOperations.class.getDeclaredMethod(
                    "requestCraft",
                    Player.class,
                    IGrid.class,
                    Level.class,
                    AeTerminalHost.class,
                    String.class,
                    long.class,
                    String.class
            );
            Object result = method.invoke(ops, player, grid, level, host, itemId, count, cpuName);
            return new CraftRequestView(
                    invokeRecordString(result, "jobId"),
                    invokeRecordString(result, "status"),
                    invokeRecordOptionalString(result, "error")
            );
        } catch (Exception exception) {
            throw new AssertionError("task12/reflection/request-craft", rootCause(exception));
        }
    }

    private static JobStatusView invokeJobStatus(AiTerminalPartOperations ops, String jobId) {
        try {
            Method method = AiTerminalPartOperations.class.getDeclaredMethod("jobStatus", String.class);
            Object result = method.invoke(ops, jobId);
            return new JobStatusView(
                    invokeRecordString(result, "jobId"),
                    invokeRecordString(result, "status"),
                    invokeRecordOptionalString(result, "error")
            );
        } catch (Exception exception) {
            throw new AssertionError("task12/reflection/job-status", rootCause(exception));
        }
    }

    private static String invokeRecordString(Object target, String accessor) {
        try {
            Method method = target.getClass().getDeclaredMethod(accessor);
            Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            throw new AssertionError("task12/reflection/record-string/" + accessor, rootCause(exception));
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeRecordOptionalString(Object target, String accessor) {
        try {
            Method method = target.getClass().getDeclaredMethod(accessor);
            Object value = method.invoke(target);
            if (value == null) {
                return Optional.empty();
            }
            if (value instanceof Optional<?> optional) {
                return (Optional<String>) optional;
            }
            throw new AssertionError("task12/reflection/record-optional-string/not-optional -> " + value.getClass().getName());
        } catch (Exception exception) {
            throw new AssertionError("task12/reflection/record-optional-string/" + accessor, rootCause(exception));
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (true) {
            if (current instanceof InvocationTargetException invocationTargetException
                    && invocationTargetException.getCause() != null) {
                current = invocationTargetException.getCause();
                continue;
            }
            if (current.getCause() != null) {
                current = current.getCause();
                continue;
            }
            return current;
        }
    }

    private record CraftRequestView(String jobId, String status, Optional<String> error) {
    }

    private record JobStatusView(String jobId, String status, Optional<String> error) {
    }

    private static IGrid newGridProxy(ControlledCraftingService controlledCraftingService) {
        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getCraftingService" -> controlledCraftingService.proxy();
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "task12-grid-proxy";
                default -> defaultValue(method.getReturnType());
            };
        };

        return (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class[]{IGrid.class},
                handler
        );
    }

    private static AeTerminalHost newTerminalHostProxy() {
        IGridNode nodeProxy = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(),
                new Class[]{IGridNode.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "task12-grid-node-proxy";
                        default -> defaultValue(method.getReturnType());
                    };
                }
        );

        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getActionableNode" -> nodeProxy;
                case "getRequestedJobs" -> ImmutableSet.of();
                case "insertCraftedItems" -> 0L;
                case "jobStateChange" -> null;
                case "getHostPos" -> BlockPos.ZERO;
                case "getHostLevel" -> null;
                case "isRemovedHost" -> false;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "task12-terminal-host-proxy";
                default -> defaultValue(method.getReturnType());
            };
        };

        return (AeTerminalHost) Proxy.newProxyInstance(
                AeTerminalHost.class.getClassLoader(),
                new Class[]{AeTerminalHost.class},
                handler
        );
    }

    private static ICraftingPlan newPlan(boolean simulation) {
        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "simulation" -> simulation;
                case "missingItems" -> null;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "task12-plan[simulation=" + simulation + "]";
                default -> defaultValue(method.getReturnType());
            };
        };

        return (ICraftingPlan) Proxy.newProxyInstance(
                ICraftingPlan.class.getClassLoader(),
                new Class[]{ICraftingPlan.class},
                handler
        );
    }

    private static Object newSubmitResult(boolean successful, ICraftingLink link) {
        Method submitMethod = requireMethod(
                resolveCraftingServiceType(),
                "submitJob"
        );
        Class<?> submitResultType = submitMethod.getReturnType();

        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "successful" -> successful;
                case "link" -> link;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "task12-submit-result[successful=" + successful + "]";
                default -> defaultValue(method.getReturnType());
            };
        };

        return Proxy.newProxyInstance(
                submitResultType.getClassLoader(),
                new Class[]{submitResultType},
                handler
        );
    }

    private static Class<?> resolveCraftingServiceType() {
        Method method = requireMethod(IGrid.class, "getCraftingService");
        return method.getReturnType();
    }

    private static Method requireMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new AssertionError("task12/reflection/method-not-found -> " + type.getName() + "#" + methodName);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static void requireTrue(String assertionName, boolean condition) {
        if (!condition) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void requireContains(String assertionName, String value, String expectedSubstring) {
        if (value != null && value.contains(expectedSubstring)) {
            return;
        }
        throw new AssertionError(
                assertionName + " -> expected substring '" + expectedSubstring + "' in value: " + value
        );
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class ControlledCraftingService {
        private final String assertionPrefix;
        private final Queue<CraftAttempt> queuedAttempts = new ArrayDeque<>();
        private final AtomicReference<CraftAttempt> currentAttempt = new AtomicReference<>();
        private final Object proxy;

        private ControlledCraftingService(String assertionPrefix) {
            this.assertionPrefix = assertionPrefix;

            Class<?> craftingServiceType = resolveCraftingServiceType();
            this.proxy = Proxy.newProxyInstance(
                    craftingServiceType.getClassLoader(),
                    new Class[]{craftingServiceType},
                    this::invoke
            );
        }

        private Object proxy() {
            return proxy;
        }

        private void enqueueSuccessfulCraft(ICraftingLink link) {
            ICraftingPlan plan = newPlan(false);
            Object submitResult = newSubmitResult(true, link);
            queuedAttempts.add(new CraftAttempt(
                    CompletableFuture.completedFuture(plan),
                    ImmutableSet.of(),
                    submitResult,
                    true
            ));
        }

        private void enqueueCalculationFailure(String failureMessage) {
            CompletableFuture<ICraftingPlan> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalStateException(failureMessage));

            queuedAttempts.add(new CraftAttempt(failedFuture, ImmutableSet.of(), null, false));
        }

        private Object invoke(Object proxyObject, Method method, Object[] args) {
            return switch (method.getName()) {
                case "beginCraftingCalculation" -> {
                    CraftAttempt attempt = queuedAttempts.poll();
                    if (attempt == null) {
                        throw new AssertionError(assertionPrefix + "/begin-crafting/no-queued-attempt");
                    }
                    currentAttempt.set(attempt);
                    yield attempt.planFuture();
                }
                case "getCpus" -> activeAttempt(assertionPrefix + "/get-cpus/no-active-attempt").cpus();
                case "submitJob" -> {
                    CraftAttempt attempt = activeAttempt(assertionPrefix + "/submit-job/no-active-attempt");
                    requireTrue(assertionPrefix + "/submit-job/unexpected-submit", attempt.submitExpected());
                    Object submitResult = attempt.submitResult();
                    if (submitResult == null) {
                        throw new AssertionError(assertionPrefix + "/submit-job/missing-submit-result");
                    }
                    currentAttempt.set(null);
                    yield submitResult;
                }
                case "equals" -> proxyObject == args[0];
                case "hashCode" -> System.identityHashCode(proxyObject);
                case "toString" -> "task12-controlled-crafting-service[" + assertionPrefix + "]";
                default -> defaultValue(method.getReturnType());
            };
        }

        private CraftAttempt activeAttempt(String assertionName) {
            CraftAttempt attempt = currentAttempt.get();
            if (attempt != null) {
                return attempt;
            }
            throw new AssertionError(assertionName);
        }
    }

    private record CraftAttempt(
            Future<ICraftingPlan> planFuture,
            ImmutableSet<ICraftingCPU> cpus,
            Object submitResult,
            boolean submitExpected
    ) {
    }

    private static final class ControlledLink {
        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final ICraftingLink link;

        private ControlledLink(String id) {
            InvocationHandler handler = (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "isCanceled" -> canceled.get();
                    case "isDone" -> done.get();
                    case "cancel" -> {
                        canceled.set(true);
                        done.set(false);
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "task12-controlled-link[" + id + "]";
                    default -> defaultValue(method.getReturnType());
                };
            };

            this.link = (ICraftingLink) Proxy.newProxyInstance(
                    ICraftingLink.class.getClassLoader(),
                    new Class[]{ICraftingLink.class},
                    handler
            );
        }

        private ICraftingLink link() {
            return link;
        }

        private void markDone() {
            done.set(true);
            canceled.set(false);
        }
    }
}
