package space.controlnet.chatmc.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.terminal.TerminalContextRegistry;
import space.controlnet.chatmc.common.terminal.TerminalContextResolver;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder("chatmc")
public final class ProposalBindingUnavailableGameTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String PLAYER_NAME = "bind_unavail";

    private ProposalBindingUnavailableGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "chatmc")
    public static void proposalBindingUnavailableApprovalFailsDeterministically(GameTestHelper helper) {
        resetSharedNetworkState();

        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(PLAYER_ID, PLAYER_NAME)
        );

        UUID sessionId = ChatMCNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
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

        AtomicReference<TerminalContextResolver> resolverRef = resolverRef();
        TerminalContextResolver previousResolver = resolverRef.getAndSet(new MapBackedResolver(liveBindings));

        try {
            requireTrue("task6/binding-unavailable/start-thinking", ChatMCNetwork.SESSIONS.tryStartThinking(sessionId));

            invokeHandleAgentLoopResult(
                    player,
                    AgentLoopResult.withProposal(proposal, 1),
                    sessionId,
                    binding,
                    "en_us"
            );

            SessionSnapshot waiting = requireSnapshot("task6/binding-unavailable/waiting", sessionId);
            requireEquals("task6/binding-unavailable/wait-state", SessionState.WAIT_APPROVAL, waiting.state());
            requireEquals(
                    "task6/binding-unavailable/wait-proposal-id",
                    Optional.of(proposal.id()),
                    waiting.pendingProposal().map(Proposal::id)
            );

            liveBindings.remove(binding);
            requireTrue(
                    "task6/binding-unavailable/binding-invalidated",
                    !liveBindings.containsKey(binding)
            );

            invokeHandleApprovalDecision(
                    player,
                    new C2SApprovalDecisionPacket(protocolVersion(), proposal.id(), ApprovalDecision.APPROVE)
            );

            SessionSnapshot failed = requireSnapshot("task6/binding-unavailable/failed", sessionId);
            requireEquals("task6/binding-unavailable/final-state", SessionState.FAILED, failed.state());
            requireEquals(
                    "task6/binding-unavailable/final-error",
                    Optional.of("bound terminal unavailable"),
                    failed.lastError()
            );
            requireTrue("task6/binding-unavailable/clears-proposal", failed.pendingProposal().isEmpty());
            requireTrue("task6/binding-unavailable/clears-binding", failed.proposalBinding().isEmpty());
            requireEquals("task6/binding-unavailable/decision-count", 1, failed.decisions().size());
            requireEquals(
                    "task6/binding-unavailable/decision-approve-recorded",
                    ApprovalDecision.APPROVE,
                    failed.decisions().get(0).decision()
            );

            ChatMessage lastMessage = failed.messages().get(failed.messages().size() - 1);
            requireEquals("task6/binding-unavailable/error-role", ChatRole.ASSISTANT, lastMessage.role());
            requireEquals(
                    "task6/binding-unavailable/error-message",
                    "Error: bound terminal unavailable",
                    lastMessage.text()
            );
            requireTrue(
                    "task6/binding-unavailable/no-tool-side-effects",
                    failed.messages().stream().noneMatch(message -> message.role() == ChatRole.TOOL)
            );

            helper.succeed();
        } finally {
            resolverRef.set(previousResolver);
            resetSharedNetworkState();
        }
    }

    private static SessionSnapshot requireSnapshot(String assertionName, UUID sessionId) {
        return ChatMCNetwork.SESSIONS.get(sessionId)
                .orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
    }

    private static int protocolVersion() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("PROTOCOL_VERSION");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception exception) {
            throw new AssertionError("task6/binding-unavailable/read-protocol-version", exception);
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
            Method method = ChatMCNetwork.class.getDeclaredMethod(
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
            throw new AssertionError("task6/binding-unavailable/invoke-handle-agent-loop-result", rootCause(exception));
        }
    }

    private static void invokeHandleApprovalDecision(ServerPlayer player, C2SApprovalDecisionPacket packet) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod(
                    "handleApprovalDecision",
                    ServerPlayer.class,
                    C2SApprovalDecisionPacket.class
            );
            method.setAccessible(true);
            method.invoke(null, player, packet);
        } catch (Exception exception) {
            throw new AssertionError("task6/binding-unavailable/invoke-handle-approval", rootCause(exception));
        }
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<TerminalContextResolver> resolverRef() {
        try {
            Field field = TerminalContextRegistry.class.getDeclaredField("RESOLVER");
            field.setAccessible(true);
            return (AtomicReference<TerminalContextResolver>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task6/binding-unavailable/read-resolver-ref", exception);
        }
    }

    private static void resetSharedNetworkState() {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
    }

    private static void clearSessionLocale() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task6/binding-unavailable/clear-session-locale", exception);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
