package space.controlnet.chatmc.ae.fabric.gametest;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import com.google.common.collect.ImmutableSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import space.controlnet.chatmc.ae.common.part.AiTerminalPartOperations;
import space.controlnet.chatmc.ae.common.terminal.AeTerminalContextResolver;
import space.controlnet.chatmc.ae.common.terminal.AeTerminalHost;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;

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

public final class ChatMCAeFabricRuntimeGameTests {
    private static final String ITEM_ID = "minecraft:stick";

    private ChatMCAeFabricRuntimeGameTests() {
    }

    public static void craftLifecycleIsolation(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setPos(0.5D, 2.0D, 0.5D);

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

        ChatMCFabricGameTestSupport.requireEquals(
                "task12/terminal-a/begin-status",
                "calculating",
                requestA.status()
        );
        ChatMCFabricGameTestSupport.requireTrue(
                "task12/terminal-a/begin-job-id",
                requestA.jobId() != null && !requestA.jobId().isBlank()
        );
        ChatMCFabricGameTestSupport.requireTrue(
                "task12/terminal-a/begin-error-empty",
                requestA.error().isEmpty()
        );
        requireUnknownOnOtherTerminal(
                "task12/terminal-a/no-cross-terminal-at-begin",
                terminalB,
                requestA.jobId()
        );

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

    public static void boundTerminalApprovalFailsWhenAeBindingUnavailable(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setPos(0.5D, 2.0D, 0.5D);

        try {
            ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                    "task14/setup/player",
                    ChatMCFabricGameTestSupport.requireNonNull("task14/setup/server", helper.getLevel().getServer()),
                    player
            );

            AtomicReference<space.controlnet.chatmc.common.terminal.TerminalContextResolver> resolverRef =
                    ChatMCFabricGameTestSupport.resolverRef();
            space.controlnet.chatmc.common.terminal.TerminalContextResolver previousResolver =
                    resolverRef.getAndSet(new AeTerminalContextResolver());

            try {
                UUID sessionId = space.controlnet.chatmc.common.ChatMCNetwork.SESSIONS.create(
                        player.getUUID(),
                        player.getGameProfile().getName()
                ).metadata().sessionId();

                TerminalBinding binding = new TerminalBinding(
                        helper.getLevel().dimension().location().toString(),
                        0,
                        64,
                        0,
                        Optional.of("NORTH")
                );
                Proposal proposal = new Proposal(
                        "task14-ae-binding-unavailable",
                        RiskLevel.SAFE_MUTATION,
                        "AE binding unavailable proposal",
                        new ToolCall("ae.request_craft", "{\"itemId\":\"minecraft:stick\",\"count\":1}"),
                        System.currentTimeMillis(),
                        ProposalDetails.empty()
                );

                ChatMCFabricGameTestSupport.requireTrue(
                        "task14/start-thinking",
                        space.controlnet.chatmc.common.ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
                );

                ChatMCFabricGameTestSupport.invokeHandleAgentLoopResult(
                        player,
                        AgentLoopResult.withProposal(proposal, 1),
                        sessionId,
                        binding,
                        "en_us"
                );

                SessionSnapshot waiting = ChatMCFabricGameTestSupport.requireSnapshot("task14/waiting", sessionId);
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/waiting-state",
                        SessionState.WAIT_APPROVAL,
                        waiting.state()
                );

                ChatMCFabricGameTestSupport.invokeHandleApprovalDecision(
                        player,
                        new C2SApprovalDecisionPacket(
                                ChatMCFabricGameTestSupport.protocolVersion(),
                                proposal.id(),
                                ApprovalDecision.APPROVE
                        )
                );

                SessionSnapshot failed = ChatMCFabricGameTestSupport.requireSnapshot("task14/failed", sessionId);
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/final-state",
                        SessionState.FAILED,
                        failed.state()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/final-error",
                        Optional.of("bound terminal unavailable"),
                        failed.lastError()
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task14/clears-proposal",
                        failed.pendingProposal().isEmpty()
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task14/clears-binding",
                        failed.proposalBinding().isEmpty()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/decision-count",
                        1,
                        failed.decisions().size()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/decision-recorded",
                        ApprovalDecision.APPROVE,
                        failed.decisions().get(0).decision()
                );

                ChatMessage lastMessage = failed.messages().get(failed.messages().size() - 1);
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/error-role",
                        ChatRole.ASSISTANT,
                        lastMessage.role()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task14/error-message",
                        "Error: bound terminal unavailable",
                        lastMessage.text()
                );

                helper.succeed();
            } finally {
                resolverRef.set(previousResolver);
            }
        } finally {
            ChatMCFabricGameTestSupport.resetRuntime();
        }
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

        ChatMCFabricGameTestSupport.requireEquals(
                "task12/terminal-b/failure-begin-status",
                "calculating",
                failedRequest.status()
        );
        ChatMCFabricGameTestSupport.requireTrue(
                "task12/terminal-b/failure-begin-job-id",
                failedRequest.jobId() != null && !failedRequest.jobId().isBlank()
        );

        awaitStatus(
                helper,
                "task12/terminal-b/wait-failed",
                terminalB,
                failedRequest.jobId(),
                "failed",
                80,
                () -> {
                    JobStatusView failedStatus = invokeJobStatus(terminalB, failedRequest.jobId());
                    ChatMCFabricGameTestSupport.requireTrue(
                            "task12/terminal-b/failure-error-present",
                            failedStatus.error().isPresent()
                    );
                    ChatMCFabricGameTestSupport.requireContains(
                            "task12/terminal-b/failure-error-signal",
                            failedStatus.error().orElse(""),
                            "forced-calc-failure"
                    );

                    ChatMCFabricGameTestSupport.requireEquals(
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

        ChatMCFabricGameTestSupport.requireEquals(
                "task12/terminal-b/recovery-begin-status",
                "calculating",
                recoveredRequest.status()
        );
        ChatMCFabricGameTestSupport.requireTrue(
                "task12/terminal-b/recovery-uses-fresh-job-id",
                !recoveredRequest.jobId().equals(failedJobId)
        );
        ChatMCFabricGameTestSupport.requireTrue(
                "task12/terminal-b/recovery-begin-error-empty",
                recoveredRequest.error().isEmpty()
        );
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
                    ChatMCFabricGameTestSupport.requireTrue(
                            "task12/terminal-b/recovery-submitted-error-empty",
                            submittedStatus.error().isEmpty()
                    );

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
                                ChatMCFabricGameTestSupport.requireEquals(
                                        "task12/terminal-b/failed-job-remains-failed-after-recovery",
                                        "failed",
                                        invokeJobStatus(terminalB, failedJobId).status()
                                );
                                ChatMCFabricGameTestSupport.requireEquals(
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
        ChatMCFabricGameTestSupport.requireEquals(assertionPrefix + "/status", "unknown", status.status());
        ChatMCFabricGameTestSupport.requireTrue(
                assertionPrefix + "/missing-job-error-present",
                status.error().isPresent()
        );
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/missing-job-error",
                "Job not found",
                status.error().orElseThrow()
        );
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

    private static IGrid newGridProxy(ControlledCraftingService controlledCraftingService) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getCraftingService" -> controlledCraftingService.proxy();
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "task12-grid-proxy";
            default -> defaultValue(method.getReturnType());
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
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "task12-grid-node-proxy";
                    default -> defaultValue(method.getReturnType());
                }
        );

        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
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

        return (AeTerminalHost) Proxy.newProxyInstance(
                AeTerminalHost.class.getClassLoader(),
                new Class[]{AeTerminalHost.class},
                handler
        );
    }

    private static ICraftingPlan newPlan(boolean simulation) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "simulation" -> simulation;
            case "missingItems" -> null;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "task12-plan[simulation=" + simulation + "]";
            default -> defaultValue(method.getReturnType());
        };

        return (ICraftingPlan) Proxy.newProxyInstance(
                ICraftingPlan.class.getClassLoader(),
                new Class[]{ICraftingPlan.class},
                handler
        );
    }

    private static Object newSubmitResult(boolean successful, ICraftingLink link) {
        Method submitMethod = requireMethod(resolveCraftingServiceType(), "submitJob");
        Class<?> submitResultType = submitMethod.getReturnType();

        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "successful" -> successful;
            case "link" -> link;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "task12-submit-result[successful=" + successful + "]";
            default -> defaultValue(method.getReturnType());
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
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private record CraftRequestView(String jobId, String status, Optional<String> error) {
    }

    private record JobStatusView(String jobId, String status, Optional<String> error) {
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
                    ChatMCFabricGameTestSupport.requireTrue(
                            assertionPrefix + "/submit-job/unexpected-submit",
                            attempt.submitExpected()
                    );
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
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
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

    private static final class ChatMCFabricGameTestSupport {
        private ChatMCFabricGameTestSupport() {
        }

        private static void initializeRuntime(GameTestHelper helper) {
            net.minecraft.server.MinecraftServer server = requireNonNull(
                    "runtime/init/server",
                    helper.getLevel().getServer()
            );
            space.controlnet.chatmc.common.ChatMCNetwork.setServer(server);
            space.controlnet.chatmc.common.ChatMCNetwork.SESSIONS.loadFromSave(
                    new space.controlnet.chatmc.core.session.PersistedSessions(1, List.of(), java.util.Map.of())
            );
            clearSessionLocale();
            clearViewerState();
        }

        private static void resetRuntime() {
            space.controlnet.chatmc.common.ChatMCNetwork.SESSIONS.loadFromSave(
                    new space.controlnet.chatmc.core.session.PersistedSessions(1, List.of(), java.util.Map.of())
            );
            clearSessionLocale();
            clearViewerState();
            space.controlnet.chatmc.common.ChatMCNetwork.setServer(null);
            clearSessionLocale();
        }

        private static void ensurePlayerResolvableByChatNetwork(
                String assertionPrefix,
                net.minecraft.server.MinecraftServer server,
                ServerPlayer player
        ) {
            UUID playerId = requireNonNull(assertionPrefix + "/id", player.getUUID());
            if (space.controlnet.chatmc.common.ChatMCNetwork.findPlayer(playerId).isPresent()) {
                return;
            }

            Object playerList = requireNonNull(assertionPrefix + "/player-list", server.getPlayerList());
            requireTrue(assertionPrefix + "/inject-player-lookup", injectPlayerLookup(playerList, playerId, player));
            requireTrue(
                    assertionPrefix + "/lookup-present",
                    space.controlnet.chatmc.common.ChatMCNetwork.findPlayer(playerId).isPresent()
            );
        }

        private static SessionSnapshot requireSnapshot(String assertionName, UUID sessionId) {
            return space.controlnet.chatmc.common.ChatMCNetwork.SESSIONS.get(sessionId)
                    .orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
        }

        private static int protocolVersion() {
            try {
                java.lang.reflect.Field field = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredField("PROTOCOL_VERSION");
                field.setAccessible(true);
                return field.getInt(null);
            } catch (Exception exception) {
                throw new AssertionError("runtime/read-protocol-version", exception);
            }
        }

        private static void invokeHandleAgentLoopResult(
                ServerPlayer player,
                AgentLoopResult result,
                UUID sessionId,
                TerminalBinding binding,
                String locale
        ) {
            try {
                Method method = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredMethod(
                        "handleAgentLoopResult",
                        ServerPlayer.class,
                        AgentLoopResult.class,
                        UUID.class,
                        TerminalBinding.class,
                        String.class
                );
                method.setAccessible(true);
                method.invoke(null, player, result, sessionId, binding, locale);
            } catch (Exception exception) {
                throw new AssertionError("runtime/invoke-handle-agent-loop-result", rootCause(exception));
            }
        }

        private static void invokeHandleApprovalDecision(ServerPlayer player, C2SApprovalDecisionPacket packet) {
            try {
                Method method = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredMethod(
                        "handleApprovalDecision",
                        ServerPlayer.class,
                        C2SApprovalDecisionPacket.class
                );
                method.setAccessible(true);
                method.invoke(null, player, packet);
            } catch (Exception exception) {
                throw new AssertionError("runtime/invoke-handle-approval", rootCause(exception));
            }
        }

        @SuppressWarnings("unchecked")
        private static AtomicReference<space.controlnet.chatmc.common.terminal.TerminalContextResolver> resolverRef() {
            try {
                java.lang.reflect.Field field = space.controlnet.chatmc.common.terminal.TerminalContextRegistry.class
                        .getDeclaredField("RESOLVER");
                field.setAccessible(true);
                return (AtomicReference<space.controlnet.chatmc.common.terminal.TerminalContextResolver>) field.get(null);
            } catch (Exception exception) {
                throw new AssertionError("runtime/read-resolver-ref", exception);
            }
        }

        private static <T> T requireNonNull(String assertionName, T value) {
            if (value != null) {
                return value;
            }
            throw new AssertionError(assertionName + " -> value must not be null");
        }

        private static void requireTrue(String assertionName, boolean condition) {
            if (condition) {
                return;
            }
            throw new AssertionError(assertionName + " -> expected true");
        }

        private static void requireEquals(String assertionName, Object expected, Object actual) {
            if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
                return;
            }
            throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
        }

        private static void requireContains(String assertionName, String value, String expectedSubstring) {
            if (value != null && value.contains(expectedSubstring)) {
                return;
            }
            throw new AssertionError(
                    assertionName + " -> expected substring '" + expectedSubstring + "' in value: " + value
            );
        }

        private static void clearViewerState() {
            viewersBySession().clear();
            sessionByViewer().clear();
        }

        private static void clearSessionLocale() {
            try {
                java.lang.reflect.Field field = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<UUID, String> localeMap = (java.util.Map<UUID, String>) field.get(null);
                localeMap.clear();
            } catch (Exception exception) {
                throw new AssertionError("runtime/clear-session-locale", exception);
            }
        }

        @SuppressWarnings("unchecked")
        private static java.util.Map<UUID, java.util.Set<UUID>> viewersBySession() {
            try {
                java.lang.reflect.Field field = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredField("VIEWERS_BY_SESSION");
                field.setAccessible(true);
                return (java.util.Map<UUID, java.util.Set<UUID>>) field.get(null);
            } catch (Exception exception) {
                throw new AssertionError("runtime/read-viewers-by-session", exception);
            }
        }

        @SuppressWarnings("unchecked")
        private static java.util.Map<UUID, UUID> sessionByViewer() {
            try {
                java.lang.reflect.Field field = space.controlnet.chatmc.common.ChatMCNetwork.class.getDeclaredField("SESSION_BY_VIEWER");
                field.setAccessible(true);
                return (java.util.Map<UUID, UUID>) field.get(null);
            } catch (Exception exception) {
                throw new AssertionError("runtime/read-session-by-viewer", exception);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static boolean injectPlayerLookup(Object playerList, UUID playerId, ServerPlayer player) {
            Class<?> currentClass = playerList.getClass();
            while (currentClass != null) {
                for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                    field.setAccessible(true);

                    try {
                        if (java.util.Map.class.isAssignableFrom(field.getType())) {
                            java.util.Map map = (java.util.Map) field.get(playerList);
                            if (map == null) {
                                continue;
                            }

                            Object previous = map.put(playerId, player);
                            if (space.controlnet.chatmc.common.ChatMCNetwork.findPlayer(playerId).isPresent()) {
                                return true;
                            }

                            if (previous == null) {
                                map.remove(playerId);
                            } else {
                                map.put(playerId, previous);
                            }
                        } else if (java.util.List.class.isAssignableFrom(field.getType())) {
                            java.util.List list = (java.util.List) field.get(playerList);
                            if (list == null) {
                                continue;
                            }

                            boolean added = false;
                            if (!list.contains(player)) {
                                list.add(player);
                                added = true;
                            }

                            if (space.controlnet.chatmc.common.ChatMCNetwork.findPlayer(playerId).isPresent()) {
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
    }
}
