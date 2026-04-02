package space.controlnet.mineagent.ae.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import space.controlnet.mineagent.ae.core.terminal.AeTerminalContext;
import space.controlnet.mineagent.ae.core.terminal.AiTerminalData;
import space.controlnet.mineagent.ae.common.terminal.AeTerminalContextResolver;
import space.controlnet.mineagent.ae.common.terminal.AeTerminalHost;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.gametest.AgentGameTestSupport;
import space.controlnet.mineagent.common.gametest.GameTestPlayerFactory;
import space.controlnet.mineagent.common.menu.AiTerminalMenu;
import space.controlnet.mineagent.common.terminal.TerminalContextResolver;
import space.controlnet.mineagent.core.agent.AgentLoopResult;
import space.controlnet.mineagent.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.ApprovalDecision;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.ChatRole;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.SessionState;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AeBindingFailureGameTestScenarios {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000141");
    private static final String PLAYER_NAME = "ae_binding_failure";
    private static final UUID SUCCESS_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000142");
    private static final String SUCCESS_PLAYER_NAME = "ae_binding_success";
    private static final UUID INVALIDATION_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000143");
    private static final String INVALIDATION_PLAYER_NAME = "ae_binding_invalidation";

    private AeBindingFailureGameTestScenarios() {
    }

    public static void boundTerminalApprovalFailsWhenAeBindingUnavailable(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, PLAYER_ID, PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        try {
            MinecraftServer server = AgentGameTestSupport.requireNonNull("task14/setup/server", helper.getLevel().getServer());
            AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task14/setup/player", server, player);

            UUID sessionId = MineAgentNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                    .metadata()
                    .sessionId();
            Proposal proposal = new Proposal(
                    "task14-ae-binding-unavailable",
                    RiskLevel.SAFE_MUTATION,
                    "AE binding unavailable proposal",
                    new ToolCall("ae.request_craft", "{\"itemId\":\"minecraft:stick\",\"count\":1}"),
                    System.currentTimeMillis(),
                    ProposalDetails.empty()
            );

            MineAgentNetwork.SESSIONS.setProposal(sessionId, proposal, null);

            SessionSnapshot waiting = AgentGameTestSupport.requireSnapshot("task14/waiting", sessionId);
            AgentGameTestSupport.requireEquals("task14/waiting-state", SessionState.WAIT_APPROVAL, waiting.state());
            AgentGameTestSupport.requireEquals(
                    "task14/waiting-proposal-id",
                    Optional.of(proposal.id()),
                    waiting.pendingProposal().map(Proposal::id)
            );
            AgentGameTestSupport.requireTrue("task14/waiting-binding-empty", waiting.proposalBinding().isEmpty());

            AgentGameTestSupport.invokeHandleApprovalDecision(
                    player,
                    new C2SApprovalDecisionPacket(
                            AgentGameTestSupport.protocolVersion(),
                            proposal.id(),
                            ApprovalDecision.APPROVE
                    )
            );

            SessionSnapshot failed = AgentGameTestSupport.requireSnapshot("task14/failed", sessionId);
            AgentGameTestSupport.requireEquals("task14/final-state", SessionState.FAILED, failed.state());
            AgentGameTestSupport.requireEquals(
                    "task14/final-error",
                    Optional.of("bound terminal unavailable"),
                    failed.lastError()
            );
            AgentGameTestSupport.requireTrue("task14/clears-proposal", failed.pendingProposal().isEmpty());
            AgentGameTestSupport.requireTrue("task14/clears-binding", failed.proposalBinding().isEmpty());
            AgentGameTestSupport.requireEquals("task14/decision-count", 1, failed.decisions().size());
            AgentGameTestSupport.requireEquals(
                    "task14/decision-recorded",
                    ApprovalDecision.APPROVE,
                    failed.decisions().get(0).decision()
            );
            AgentGameTestSupport.requireTrue(
                    "task14/no-tool-side-effects",
                    failed.messages().stream().noneMatch(message -> message.role() == ChatRole.TOOL)
            );

            ChatMessage lastMessage = failed.messages().get(failed.messages().size() - 1);
            AgentGameTestSupport.requireEquals("task14/error-role", ChatRole.ASSISTANT, lastMessage.role());
            AgentGameTestSupport.requireEquals(
                    "task14/error-message",
                    "Error: bound terminal unavailable",
                    lastMessage.text()
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    public static void boundTerminalApprovalSuccessHandoff(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, SUCCESS_PLAYER_ID, SUCCESS_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = AgentGameTestSupport.requireNonNull("task16/approval-success/setup/server", helper.getLevel().getServer());
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task16/approval-success/setup/player", server, player);

        AtomicReference<space.controlnet.mineagent.common.terminal.TerminalContextResolver> resolverRef =
                AgentGameTestSupport.resolverRef();
        space.controlnet.mineagent.common.terminal.TerminalContextResolver previousResolver = resolverRef.get();

        TerminalBinding binding = new TerminalBinding(
                helper.getLevel().dimension().location().toString(),
                0,
                64,
                0,
                Optional.of("NORTH")
        );

        RecordingAeTerminalContext terminalContext = new RecordingAeTerminalContext();
        SuccessfulResumeRunner resumeRunner = new SuccessfulResumeRunner("ae approval handoff complete");
        AgentRunnerState previousRunner = replaceAgentRunner(resumeRunner);
        resolverRef.set(new FixedBindingResolver(binding, terminalContext));

        Runnable cleanup = runOnce(() -> {
            resolverRef.set(previousResolver);
            restoreAgentRunner(previousRunner);
            AgentGameTestSupport.resetRuntime();
        });

        try {
            UUID sessionId = MineAgentNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                    .metadata()
                    .sessionId();

            Proposal proposal = new Proposal(
                    "task16-ae-binding-success",
                    RiskLevel.SAFE_MUTATION,
                    "AE binding approval success proposal",
                    new ToolCall("ae.request_craft", "{\"itemId\":\"minecraft:stick\",\"count\":1}"),
                    System.currentTimeMillis(),
                    ProposalDetails.empty()
            );

            AgentGameTestSupport.requireTrue(
                    "task16/approval-success/start-thinking",
                    MineAgentNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            AgentGameTestSupport.invokeHandleAgentLoopResult(
                    player,
                    AgentLoopResult.withProposal(proposal, 1),
                    sessionId,
                    binding,
                    "en_us"
            );

            SessionSnapshot waiting = AgentGameTestSupport.requireSnapshot("task16/approval-success/waiting", sessionId);
            AgentGameTestSupport.requireEquals("task16/approval-success/wait-state", SessionState.WAIT_APPROVAL, waiting.state());

            AgentGameTestSupport.invokeHandleApprovalDecision(
                    player,
                    new C2SApprovalDecisionPacket(
                            AgentGameTestSupport.protocolVersion(),
                            proposal.id(),
                            ApprovalDecision.APPROVE
                    )
            );

            awaitSessionState(helper, "task16/approval-success/wait-done", sessionId, SessionState.DONE, 80, () -> {
                try {
                    SessionSnapshot completed = AgentGameTestSupport.requireSnapshot("task16/approval-success/completed", sessionId);
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/final-state",
                            SessionState.DONE,
                            completed.state()
                    );
                    AgentGameTestSupport.requireTrue(
                            "task16/approval-success/clears-proposal",
                            completed.pendingProposal().isEmpty()
                    );
                    AgentGameTestSupport.requireTrue(
                            "task16/approval-success/clears-binding",
                            completed.proposalBinding().isEmpty()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/decision-count",
                            1,
                            completed.decisions().size()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/decision-recorded",
                            ApprovalDecision.APPROVE,
                            completed.decisions().get(0).decision()
                    );

                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/request-count",
                            1,
                            terminalContext.requestCount.get()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/request-item",
                            "minecraft:stick",
                            terminalContext.lastItemId.get()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/request-count-value",
                            1L,
                            terminalContext.lastCount.get()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/request-cpu",
                            null,
                            terminalContext.lastCpuName.get()
                    );

                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/resume-count",
                            1,
                            resumeRunner.invocationCount.get()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/resume-binding",
                            binding,
                            resumeRunner.lastBinding.get()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/resume-locale",
                            "en_us",
                            resumeRunner.lastLocale.get()
                    );

                    ChatMessage toolMessage = requireLastMessageOfRole(
                            "task16/approval-success/tool-message",
                            completed.messages(),
                            ChatRole.TOOL
                    );
                    AgentGameTestSupport.requireContains(
                            "task16/approval-success/tool-message-tool-name",
                            toolMessage.text(),
                            "ae.request_craft"
                    );
                    AgentGameTestSupport.requireContains(
                            "task16/approval-success/tool-message-job-id",
                            toolMessage.text(),
                            "job-approval-success"
                    );

                    ChatMessage assistantMessage = completed.messages().get(completed.messages().size() - 1);
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/assistant-role",
                            ChatRole.ASSISTANT,
                            assistantMessage.role()
                    );
                    AgentGameTestSupport.requireEquals(
                            "task16/approval-success/assistant-message",
                            "ae approval handoff complete",
                            assistantMessage.text()
                    );

                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            }, cleanup);
        } catch (Throwable throwable) {
            cleanup.run();
            throw throwable;
        }
    }

    public static void bindingInvalidationAfterTerminalRemovalOrWrongSide(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, INVALIDATION_PLAYER_ID, INVALIDATION_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        try {
            MutableAeTerminalHost host = new MutableAeTerminalHost();
            host.hostLevel.set(helper.getLevel());
            host.hostPos.set(player.blockPosition());
            setContainerMenu(player, new AiTerminalMenu(41, player.getInventory(), host.proxy(), player.blockPosition(), null));

            AeTerminalContextResolver resolver = new AeTerminalContextResolver();
            AgentGameTestSupport.requireTrue(
                    "task16/binding-invalidation/live-menu-context-present",
                    resolver.fromPlayer(player).isPresent()
            );

            host.removed.set(true);
            AgentGameTestSupport.requireTrue(
                    "task16/binding-invalidation/removed-menu-context-empty",
                    resolver.fromPlayer(player).isEmpty()
            );

            TerminalBinding wrongSideBinding = new TerminalBinding(
                    helper.getLevel().dimension().location().toString(),
                    player.blockPosition().getX(),
                    player.blockPosition().getY(),
                    player.blockPosition().getZ(),
                    Optional.of("SOUTH")
            );
            AgentGameTestSupport.requireTrue(
                    "task16/binding-invalidation/wrong-side-binding-empty",
                    resolver.fromPlayerAtBinding(player, wrongSideBinding).isEmpty()
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    private static AgentRunnerState replaceAgentRunner(Object runner) {
        try {
            Field agentField = MineAgentNetwork.class.getDeclaredField("AGENT");
            agentField.setAccessible(true);
            Object agentInvoker = agentField.get(null);

            Field runnerField = agentInvoker.getClass().getDeclaredField("runner");
            runnerField.setAccessible(true);
            Field initAttemptedField = agentInvoker.getClass().getDeclaredField("initAttempted");
            initAttemptedField.setAccessible(true);

            Object previousRunner = runnerField.get(agentInvoker);
            boolean previousInitAttempted = initAttemptedField.getBoolean(agentInvoker);

            runnerField.set(agentInvoker, runner);
            initAttemptedField.setBoolean(agentInvoker, true);

            return new AgentRunnerState(previousRunner, previousInitAttempted);
        } catch (Exception exception) {
            throw new AssertionError("task16/approval-success/install-agent-runner", AgentGameTestSupport.rootCause(exception));
        }
    }

    private static void restoreAgentRunner(AgentRunnerState previousState) {
        try {
            Field agentField = MineAgentNetwork.class.getDeclaredField("AGENT");
            agentField.setAccessible(true);
            Object agentInvoker = agentField.get(null);

            Field runnerField = agentInvoker.getClass().getDeclaredField("runner");
            runnerField.setAccessible(true);
            Field initAttemptedField = agentInvoker.getClass().getDeclaredField("initAttempted");
            initAttemptedField.setAccessible(true);

            runnerField.set(agentInvoker, previousState.runner());
            initAttemptedField.setBoolean(agentInvoker, previousState.initAttempted());
        } catch (Exception exception) {
            throw new AssertionError("task16/approval-success/restore-agent-runner", AgentGameTestSupport.rootCause(exception));
        }
    }

    private static void setContainerMenu(ServerPlayer player, AiTerminalMenu menu) {
        Class<?> currentClass = Player.class;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField("containerMenu");
                field.setAccessible(true);
                field.set(player, menu);
                return;
            } catch (NoSuchFieldException noSuchFieldException) {
                currentClass = currentClass.getSuperclass();
            } catch (Exception exception) {
                throw new AssertionError("task16/binding-invalidation/set-container-menu", AgentGameTestSupport.rootCause(exception));
            }
        }
        throw new AssertionError("task16/binding-invalidation/set-container-menu -> field not found");
    }

    private static ChatMessage requireLastMessageOfRole(String assertionName, List<ChatMessage> messages, ChatRole role) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message.role() == role) {
                return message;
            }
        }
        throw new AssertionError(assertionName + " -> missing role " + role);
    }

    private static void awaitSessionState(
            GameTestHelper helper,
            String assertionName,
            UUID sessionId,
            SessionState expectedState,
            int ticksRemaining,
            Runnable onReady,
            Runnable onFailure
    ) {
        try {
            SessionSnapshot snapshot = AgentGameTestSupport.requireSnapshot(assertionName + "/snapshot", sessionId);
            if (snapshot.state() == expectedState) {
                onReady.run();
                return;
            }

            if (ticksRemaining <= 0) {
                throw new AssertionError(
                        assertionName
                                + " -> timed out waiting for state='"
                                + expectedState
                                + "', actual='"
                                + snapshot.state()
                                + "'"
                );
            }

            helper.runAfterDelay(
                    1,
                    () -> awaitSessionState(helper, assertionName, sessionId, expectedState, ticksRemaining - 1, onReady, onFailure)
            );
        } catch (Throwable throwable) {
            onFailure.run();
            throw throwable;
        }
    }

    private static Runnable runOnce(Runnable action) {
        AtomicBoolean ran = new AtomicBoolean(false);
        return () -> {
            if (ran.compareAndSet(false, true)) {
                action.run();
            }
        };
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

    private static final class FixedBindingResolver implements TerminalContextResolver {
        private final TerminalBinding binding;
        private final TerminalContext terminalContext;

        private FixedBindingResolver(TerminalBinding binding, TerminalContext terminalContext) {
            this.binding = binding;
            this.terminalContext = terminalContext;
        }

        @Override
        public Optional<TerminalContext> fromPlayer(ServerPlayer player) {
            return Optional.empty();
        }

        @Override
        public Optional<TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding) {
            if (this.binding.equals(binding)) {
                return Optional.of(terminalContext);
            }
            return Optional.empty();
        }
    }

    private static final class RecordingAeTerminalContext implements AeTerminalContext {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> lastItemId = new AtomicReference<>();
        private final AtomicReference<Long> lastCount = new AtomicReference<>(0L);
        private final AtomicReference<String> lastCpuName = new AtomicReference<>();

        @Override
        public AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, String pageToken) {
            return new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.empty());
        }

        @Override
        public AiTerminalData.AeListResult listCraftables(String query, int limit, String pageToken) {
            return new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.empty());
        }

        @Override
        public AiTerminalData.AeCraftSimulation simulateCraft(String itemId, long count) {
            return new AiTerminalData.AeCraftSimulation("", "error", List.of(), Optional.of("not used"));
        }

        @Override
        public AiTerminalData.AeCraftRequest requestCraft(String itemId, long count, String cpuName) {
            requestCount.incrementAndGet();
            lastItemId.set(itemId);
            lastCount.set(count);
            lastCpuName.set(cpuName);
            return new AiTerminalData.AeCraftRequest("job-approval-success", "submitted", Optional.empty());
        }

        @Override
        public AiTerminalData.AeJobStatus jobStatus(String jobId) {
            return new AiTerminalData.AeJobStatus(jobId, "submitted", List.of(), Optional.empty());
        }

        @Override
        public AiTerminalData.AeJobStatus cancelJob(String jobId) {
            return new AiTerminalData.AeJobStatus(jobId, "canceled", List.of(), Optional.empty());
        }
    }

    public static final class SuccessfulResumeRunner {
        private final String assistantMessage;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicReference<TerminalBinding> lastBinding = new AtomicReference<>();
        private final AtomicReference<String> lastLocale = new AtomicReference<>();

        public SuccessfulResumeRunner(String assistantMessage) {
            this.assistantMessage = assistantMessage;
        }

        public AgentLoopResult runLoop(ServerPlayer player, UUID sessionId, TerminalBinding binding, String effectiveLocale) {
            invocationCount.incrementAndGet();
            lastBinding.set(binding);
            lastLocale.set(effectiveLocale);
            MineAgentNetwork.SESSIONS.appendMessage(
                    sessionId,
                    new ChatMessage(ChatRole.ASSISTANT, assistantMessage, System.currentTimeMillis())
            );
            return AgentLoopResult.withResponse(assistantMessage, 2);
        }
    }

    private record AgentRunnerState(Object runner, boolean initAttempted) {
    }

    private static final class MutableAeTerminalHost {
        private final AtomicReference<AeTerminalHost> proxy = new AtomicReference<>();
        private final AtomicReference<net.minecraft.world.level.Level> hostLevel = new AtomicReference<>();
        private final AtomicReference<net.minecraft.core.BlockPos> hostPos = new AtomicReference<>(net.minecraft.core.BlockPos.ZERO);
        private final AtomicReference<String> toStringValue = new AtomicReference<>("task16-mutable-ae-host");
        private final java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean(false);

        private MutableAeTerminalHost() {
            InvocationHandler handler = (proxyInstance, method, args) -> switch (method.getName()) {
                case "getHostPos" -> hostPos.get();
                case "getHostLevel" -> hostLevel.get();
                case "isRemovedHost" -> removed.get();
                case "listItems" -> new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.empty());
                case "listCraftables" -> new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.empty());
                case "simulateCraft" -> new AiTerminalData.AeCraftSimulation("", "error", List.of(), Optional.of("not used"));
                case "requestCraft" -> new AiTerminalData.AeCraftRequest("", "error", Optional.of("not used"));
                case "jobStatus" -> new AiTerminalData.AeJobStatus("", "unknown", List.of(), Optional.of("not used"));
                case "cancelJob" -> new AiTerminalData.AeJobStatus("", "canceled", List.of(), Optional.empty());
                case "getRequestedJobs" -> com.google.common.collect.ImmutableSet.of();
                case "insertCraftedItems" -> 0L;
                case "jobStateChange" -> null;
                case "equals" -> proxyInstance == args[0];
                case "hashCode" -> System.identityHashCode(proxyInstance);
                case "toString" -> toStringValue.get();
                default -> defaultValue(method.getReturnType());
            };

            proxy.set((AeTerminalHost) Proxy.newProxyInstance(
                    AeTerminalHost.class.getClassLoader(),
                    new Class[]{AeTerminalHost.class},
                    handler
            ));
        }

        private AeTerminalHost proxy() {
            return proxy.get();
        }
    }
}
