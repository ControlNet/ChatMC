package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.MineAgentNetwork;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ProposalBindingUnavailableGameTestScenarios {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String PLAYER_NAME = "bind_unavail";

    private ProposalBindingUnavailableGameTestScenarios() {
    }

    public static void proposalBindingUnavailableApprovalFailsDeterministically(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, PLAYER_ID, PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task6/binding-unavailable/setup/server",
                helper.getLevel().getServer()
        );
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task6/binding-unavailable/setup/player",
                server,
                player
        );

        UUID sessionId = MineAgentNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                .metadata()
                .sessionId();

        TerminalBinding binding = new TerminalBinding(
                helper.getLevel().dimension().location().toString(),
                0,
                64,
                0,
                Optional.of("NORTH")
        );

        Proposal proposal = new Proposal(
                "proposal-binding-unavailable",
                RiskLevel.SAFE_MUTATION,
                "binding unavailable proposal",
                new ToolCall("mc.binding_probe", "{\"probe\":true}"),
                System.currentTimeMillis(),
                ProposalDetails.empty()
        );

        Map<TerminalBinding, TerminalContext> liveBindings = new ConcurrentHashMap<>();
        liveBindings.put(binding, new TerminalContext() {
        });

        AtomicReference<TerminalContextResolver> resolverRef = AgentGameTestSupport.resolverRef();
        TerminalContextResolver previousResolver = resolverRef.getAndSet(new MapBackedResolver(liveBindings));

        try {
            AgentGameTestSupport.requireTrue(
                    "task6/binding-unavailable/start-thinking",
                    MineAgentNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            AgentGameTestSupport.invokeHandleAgentLoopResult(
                    player,
                    AgentLoopResult.withProposal(proposal, 1),
                    sessionId,
                    binding,
                    "en_us"
            );

            SessionSnapshot waiting = AgentGameTestSupport.requireSnapshot("task6/binding-unavailable/waiting", sessionId);
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/wait-state",
                    SessionState.WAIT_APPROVAL,
                    waiting.state()
            );
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/wait-proposal-id",
                    Optional.of(proposal.id()),
                    waiting.pendingProposal().map(Proposal::id)
            );

            liveBindings.remove(binding);
            AgentGameTestSupport.requireTrue(
                    "task6/binding-unavailable/binding-invalidated",
                    !liveBindings.containsKey(binding)
            );

            AgentGameTestSupport.invokeHandleApprovalDecision(
                    player,
                    new C2SApprovalDecisionPacket(
                            AgentGameTestSupport.protocolVersion(),
                            proposal.id(),
                            ApprovalDecision.APPROVE
                    )
            );

            SessionSnapshot failed = AgentGameTestSupport.requireSnapshot("task6/binding-unavailable/failed", sessionId);
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/final-state",
                    SessionState.FAILED,
                    failed.state()
            );
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/final-error",
                    Optional.of("bound terminal unavailable"),
                    failed.lastError()
            );
            AgentGameTestSupport.requireTrue(
                    "task6/binding-unavailable/clears-proposal",
                    failed.pendingProposal().isEmpty()
            );
            AgentGameTestSupport.requireTrue(
                    "task6/binding-unavailable/clears-binding",
                    failed.proposalBinding().isEmpty()
            );
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/decision-count",
                    1,
                    failed.decisions().size()
            );
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/decision-approve-recorded",
                    ApprovalDecision.APPROVE,
                    failed.decisions().get(0).decision()
            );

            ChatMessage lastMessage = failed.messages().get(failed.messages().size() - 1);
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/error-role",
                    ChatRole.ASSISTANT,
                    lastMessage.role()
            );
            AgentGameTestSupport.requireEquals(
                    "task6/binding-unavailable/error-message",
                    "Error: bound terminal unavailable",
                    lastMessage.text()
            );
            AgentGameTestSupport.requireTrue(
                    "task6/binding-unavailable/no-tool-side-effects",
                    failed.messages().stream().noneMatch(message -> message.role() == ChatRole.TOOL)
            );

            helper.succeed();
        } finally {
            resolverRef.set(previousResolver);
            AgentGameTestSupport.resetRuntime();
        }
    }

    private static final class MapBackedResolver implements TerminalContextResolver {
        private final Map<TerminalBinding, TerminalContext> liveBindings;

        private MapBackedResolver(Map<TerminalBinding, TerminalContext> liveBindings) {
            this.liveBindings = liveBindings;
        }

        @Override
        public Optional<TerminalContext> fromPlayer(ServerPlayer player) {
            return Optional.empty();
        }

        @Override
        public Optional<TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding) {
            return Optional.ofNullable(liveBindings.get(binding));
        }
    }
}
