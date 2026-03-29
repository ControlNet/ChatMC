package space.controlnet.chatmc.common.network;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.controlnet.chatmc.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.DecisionLogEntry;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionMetadata;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.SessionVisibility;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NetworkProposalLifecycleBehaviorTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final String PLAYER_NAME = "task7-proposal-behavior";

    @BeforeAll
    static void bootstrapMinecraft() {
        ensureMinecraftBootstrap();
    }

    @BeforeEach
    void resetSharedNetworkState() {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
    }

    @Test
    void task7_proposalLifecycleBehavior_proposalResultTransitionsThinkingToWaitApproval() {
        UUID sessionId = newSession();
        Proposal proposal = proposal("proposal-accepted", "mc.craft", "{\"item\":\"minecraft:stick\"}");
        TerminalBinding binding = binding("north");

        assertTrue("task7/proposal-lifecycle-behavior/start-thinking",
                ChatMCNetwork.SESSIONS.tryStartThinking(sessionId));

        invokeHandleAgentLoopResult(AgentLoopResult.withProposal(proposal, 1), sessionId, binding, "en_us");

        SessionSnapshot waiting = requireSnapshot("task7/proposal-lifecycle-behavior/waiting", sessionId);
        assertEquals("task7/proposal-lifecycle-behavior/state-wait-approval",
                SessionState.WAIT_APPROVAL, waiting.state());
        assertEquals("task7/proposal-lifecycle-behavior/proposal-id",
                proposal.id(), waiting.pendingProposal().orElseThrow().id());
        assertEquals("task7/proposal-lifecycle-behavior/binding-side",
                Optional.of("north"), waiting.proposalBinding().orElseThrow().side());
    }

    @Test
    void task7_proposalLifecycleBehavior_rejectedTransitionWritesDeterministicErrorMessage() {
        UUID sessionId = newSession();
        Proposal proposal = proposal("proposal-rejected", "mc.move", "{\"count\":1}");
        setSessionLocale(sessionId, "de_de");

        invokeHandleAgentLoopResult(AgentLoopResult.withProposal(proposal, 1), sessionId, binding("west"), "de_de");

        SessionSnapshot failed = requireSnapshot("task7/proposal-lifecycle-behavior/rejected", sessionId);
        assertEquals("task7/proposal-lifecycle-behavior/rejected-state",
                SessionState.FAILED, failed.state());
        assertEquals("task7/proposal-lifecycle-behavior/rejected-last-error",
                Optional.of("Session transition rejected proposal result"), failed.lastError());
        assertEquals("task7/proposal-lifecycle-behavior/rejected-appended-role",
                ChatRole.ASSISTANT, failed.messages().get(failed.messages().size() - 1).role());
        assertEquals("task7/proposal-lifecycle-behavior/rejected-appended-text",
                "Error: Session transition rejected proposal result",
                failed.messages().get(failed.messages().size() - 1).text());
        assertTrue("task7/proposal-lifecycle-behavior/rejected-clears-locale",
                !hasSessionLocale(sessionId));
    }

    @Test
    void task7_proposalLifecycleBehavior_executionResumeAndResponseFlowEndsInDone() {
        UUID sessionId = newSession();
        Proposal first = proposal("proposal-first", "mc.deposit", "{\"slot\":2}");
        Proposal resumed = proposal("proposal-resumed", "mc.deposit", "{\"slot\":3}");

        assertTrue("task7/proposal-lifecycle-behavior/resume-start-thinking",
                ChatMCNetwork.SESSIONS.tryStartThinking(sessionId));
        invokeHandleAgentLoopResult(AgentLoopResult.withProposal(first, 1), sessionId, binding("south"), "en_us");

        assertTrue("task7/proposal-lifecycle-behavior/start-executing",
                ChatMCNetwork.SESSIONS.tryStartExecuting(sessionId, first.id()));

        ChatMCNetwork.SESSIONS.clearProposalPreserveState(sessionId);
        SessionSnapshot executingCleared = requireSnapshot(
                "task7/proposal-lifecycle-behavior/executing-cleared",
                sessionId
        );
        assertEquals("task7/proposal-lifecycle-behavior/clear-preserve-keeps-executing",
                SessionState.EXECUTING, executingCleared.state());
        assertTrue("task7/proposal-lifecycle-behavior/clear-preserve-clears-pending",
                executingCleared.pendingProposal().isEmpty());

        invokeHandleAgentLoopResult(AgentLoopResult.withProposal(resumed, 2), sessionId, binding("east"), "en_us");
        SessionSnapshot resumedWaiting = requireSnapshot("task7/proposal-lifecycle-behavior/resumed-wait", sessionId);
        assertEquals("task7/proposal-lifecycle-behavior/resumed-state", SessionState.WAIT_APPROVAL, resumedWaiting.state());
        assertEquals("task7/proposal-lifecycle-behavior/resumed-proposal-id",
                resumed.id(), resumedWaiting.pendingProposal().orElseThrow().id());

        setSessionLocale(sessionId, "en_us");
        invokeHandleAgentLoopResult(AgentLoopResult.withResponse("assistant-finished", 3), sessionId, null, "en_us");

        SessionSnapshot done = requireSnapshot("task7/proposal-lifecycle-behavior/final-done", sessionId);
        assertEquals("task7/proposal-lifecycle-behavior/final-state-done", SessionState.DONE, done.state());
        assertTrue("task7/proposal-lifecycle-behavior/response-clears-locale",
                !hasSessionLocale(sessionId));
    }

    @Test
    void task7_snapshotRoundTrip_preservesProposalBindingAndDecisionHistory() {
        SessionSnapshot snapshot = new SessionSnapshot(
                new SessionMetadata(
                        UUID.fromString("00000000-0000-0000-0000-000000000721"),
                        PLAYER_ID,
                        PLAYER_NAME,
                        SessionVisibility.PUBLIC,
                        Optional.of("task7-team"),
                        "Task 7 Snapshot",
                        100L,
                        200L
                ),
                List.of(
                        new ChatMessage(ChatRole.USER, "craft sticks", 101L),
                        new ChatMessage(ChatRole.ASSISTANT, "proposal ready", 102L)
                ),
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal("proposal-snapshot", "mc.craft", "{\"item\":\"minecraft:stick\"}")),
                Optional.of(binding("north")),
                List.of(
                        new DecisionLogEntry(103L, Optional.of(PLAYER_ID), Optional.of(PLAYER_NAME),
                                "proposal-snapshot", Optional.of("mc.craft"), ApprovalDecision.APPROVE),
                        new DecisionLogEntry(104L, Optional.empty(), Optional.of("observer"),
                                "proposal-snapshot", Optional.empty(), ApprovalDecision.DENY)
                ),
                Optional.of("sticky-error")
        );

        SessionSnapshot decoded = invokeSnapshotRoundTrip(snapshot);

        assertEquals("task7/snapshot-roundtrip/session-id", snapshot.metadata().sessionId(), decoded.metadata().sessionId());
        assertEquals("task7/snapshot-roundtrip/visibility", SessionVisibility.PUBLIC, decoded.metadata().visibility());
        assertEquals("task7/snapshot-roundtrip/team-id", Optional.of("task7-team"), decoded.metadata().teamId());
        assertEquals("task7/snapshot-roundtrip/state", SessionState.WAIT_APPROVAL, decoded.state());
        assertEquals("task7/snapshot-roundtrip/messages-size", 2, decoded.messages().size());
        assertEquals("task7/snapshot-roundtrip/proposal-id",
                "proposal-snapshot", decoded.pendingProposal().orElseThrow().id());
        assertEquals("task7/snapshot-roundtrip/proposal-tool",
                "mc.craft", decoded.pendingProposal().orElseThrow().toolCall().toolName());
        assertEquals("task7/snapshot-roundtrip/binding-side",
                Optional.of("north"), decoded.proposalBinding().orElseThrow().side());
        assertEquals("task7/snapshot-roundtrip/decisions-size", 2, decoded.decisions().size());
        assertEquals("task7/snapshot-roundtrip/decision-0-name",
                Optional.of(PLAYER_NAME), decoded.decisions().get(0).playerName());
        assertEquals("task7/snapshot-roundtrip/decision-1-tool-name",
                Optional.empty(), decoded.decisions().get(1).toolName());
        assertEquals("task7/snapshot-roundtrip/decision-1-outcome",
                ApprovalDecision.DENY, decoded.decisions().get(1).decision());
        assertEquals("task7/snapshot-roundtrip/error", Optional.of("sticky-error"), decoded.lastError());
    }

    @Test
    void task7_approvalDecisionBehavior_denyClearsProposalAndRecordsDecision() {
        UUID sessionId = newSession();
        Proposal proposal = proposal("proposal-deny", "mc.move", "{\"slot\":1}");
        ChatMCNetwork.SESSIONS.setProposal(sessionId, proposal, binding("west"));
        ChatMCNetwork.SESSIONS.setActive(PLAYER_ID, sessionId);

        invokeHandleApprovalDecision(fakePlayer(PLAYER_ID, PLAYER_NAME),
                new C2SApprovalDecisionPacket(protocolVersion(), proposal.id(), ApprovalDecision.DENY));

        SessionSnapshot denied = requireSnapshot("task7/approval-decision/deny", sessionId);
        assertEquals("task7/approval-decision/deny-state", SessionState.IDLE, denied.state());
        assertTrue("task7/approval-decision/deny-clears-proposal", denied.pendingProposal().isEmpty());
        assertTrue("task7/approval-decision/deny-clears-binding", denied.proposalBinding().isEmpty());
        assertEquals("task7/approval-decision/deny-decision-count", 1, denied.decisions().size());
        assertEquals("task7/approval-decision/deny-decision",
                ApprovalDecision.DENY, denied.decisions().get(0).decision());
        assertEquals("task7/approval-decision/deny-player-name",
                Optional.of(PLAYER_NAME), denied.decisions().get(0).playerName());
    }

    @Test
    void task7_approvalDecisionBehavior_approveWithoutBindingFailsDeterministically() {
        UUID sessionId = newSession();
        Proposal proposal = proposal("proposal-approve-no-binding", "mc.deposit", "{\"slot\":2}");
        ChatMCNetwork.SESSIONS.setProposal(sessionId, proposal, null);
        ChatMCNetwork.SESSIONS.setActive(PLAYER_ID, sessionId);

        invokeHandleApprovalDecision(fakePlayer(PLAYER_ID, PLAYER_NAME),
                new C2SApprovalDecisionPacket(protocolVersion(), proposal.id(), ApprovalDecision.APPROVE));

        SessionSnapshot failed = requireSnapshot("task7/approval-decision/approve-no-binding", sessionId);
        assertEquals("task7/approval-decision/approve-no-binding-state", SessionState.FAILED, failed.state());
        assertEquals("task7/approval-decision/approve-no-binding-error",
                Optional.of("bound terminal unavailable"), failed.lastError());
        assertEquals("task7/approval-decision/approve-no-binding-message",
                "Error: bound terminal unavailable",
                failed.messages().get(failed.messages().size() - 1).text());
        assertEquals("task7/approval-decision/approve-no-binding-decision",
                ApprovalDecision.APPROVE, failed.decisions().get(0).decision());
    }

    @Test
    void task7_viewerMapping_reassignAndUnsubscribeCleansStaleEntries() {
        UUID sessionOne = UUID.fromString("00000000-0000-0000-0000-000000000731");
        UUID sessionTwo = UUID.fromString("00000000-0000-0000-0000-000000000732");
        UUID viewerOne = UUID.fromString("00000000-0000-0000-0000-000000000733");
        UUID viewerTwo = UUID.fromString("00000000-0000-0000-0000-000000000734");

        invokeSubscribeViewer(viewerOne, sessionOne);
        invokeSubscribeViewer(viewerTwo, sessionOne);
        invokeSubscribeViewer(viewerOne, sessionTwo);

        assertEquals("task7/viewer-mapping/session-one-viewers-after-reassign",
                java.util.Set.of(viewerTwo), viewersBySession().get(sessionOne));
        assertEquals("task7/viewer-mapping/session-two-viewers-after-reassign",
                java.util.Set.of(viewerOne), viewersBySession().get(sessionTwo));
        assertEquals("task7/viewer-mapping/viewer-one-current-session",
                sessionTwo, sessionByViewer().get(viewerOne));

        invokeUnsubscribeViewer(UUID.fromString("00000000-0000-0000-0000-000000000735"));
        invokeUnsubscribeViewer(viewerTwo);
        invokeUnsubscribeViewer(viewerOne);

        assertTrue("task7/viewer-mapping/session-map-empty", viewersBySession().isEmpty());
        assertTrue("task7/viewer-mapping/viewer-map-empty", sessionByViewer().isEmpty());
    }

    @Test
    void task7_deletedSession_lateAgentCallbacks_doNotRecreateSession() {
        UUID sessionId = newSession();
        setSessionLocale(sessionId, "fr_fr");

        ChatMCNetwork.SESSIONS.delete(sessionId);

        invokeHandleAgentLoopResult(AgentLoopResult.withResponse("late-response", 4), sessionId, null, "fr_fr");
        assertTrue("task7/deleted-session/late-response-does-not-recreate",
                ChatMCNetwork.SESSIONS.get(sessionId).isEmpty());
        assertTrue("task7/deleted-session/late-response-clears-locale",
                !hasSessionLocale(sessionId));

        setSessionLocale(sessionId, "fr_fr");
        invokeApplyAgentError(sessionId, "late-error");
        assertTrue("task7/deleted-session/late-error-does-not-recreate",
                ChatMCNetwork.SESSIONS.get(sessionId).isEmpty());
        assertTrue("task7/deleted-session/late-error-clears-locale",
                !hasSessionLocale(sessionId));
    }

    @Test
    void task7_deletedSession_asyncAppendPath_doesNotRecreateSession() {
        UUID sessionId = newSession();
        ChatMCNetwork.SESSIONS.delete(sessionId);

        ChatMCNetwork.appendMessageAndBroadcast(sessionId,
                new ChatMessage(ChatRole.TOOL, "tool-payload", System.currentTimeMillis()));
        ChatMCNetwork.appendMessageAndBroadcast(sessionId,
                new ChatMessage(ChatRole.ASSISTANT, "assistant-response", System.currentTimeMillis()));

        assertTrue("task7/deleted-session/append-path-tool-does-not-recreate",
                ChatMCNetwork.SESSIONS.get(sessionId).isEmpty());
    }

    private static UUID newSession() {
        return ChatMCNetwork.SESSIONS.create(PLAYER_ID, PLAYER_NAME).metadata().sessionId();
    }

    private static void invokeHandleAgentLoopResult(
            AgentLoopResult result,
            UUID sessionId,
            TerminalBinding binding,
            String effectiveLocale
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
            method.invoke(null, null, result, sessionId, binding, effectiveLocale);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-handle-agent-loop-result", rootCause(exception));
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
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-handle-approval-decision", rootCause(exception));
        }
    }

    private static void invokeApplyAgentError(UUID sessionId, String message) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("applyAgentError", UUID.class, String.class);
            method.setAccessible(true);
            method.invoke(null, sessionId, message);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-apply-agent-error", rootCause(exception));
        }
    }

    private static SessionSnapshot invokeSnapshotRoundTrip(SessionSnapshot snapshot) {
        try {
            Method write = ChatMCNetwork.class.getDeclaredMethod("writeSnapshot", FriendlyByteBuf.class, SessionSnapshot.class);
            Method read = ChatMCNetwork.class.getDeclaredMethod("readSnapshot", FriendlyByteBuf.class);
            write.setAccessible(true);
            read.setAccessible(true);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            write.invoke(null, buffer, snapshot);
            return (SessionSnapshot) read.invoke(null, buffer);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-snapshot-roundtrip", rootCause(exception));
        }
    }

    private static void invokeSubscribeViewer(UUID viewerId, UUID sessionId) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("subscribeViewer", UUID.class, UUID.class);
            method.setAccessible(true);
            method.invoke(null, viewerId, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-subscribe-viewer", rootCause(exception));
        }
    }

    private static void invokeUnsubscribeViewer(UUID viewerId) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("unsubscribeViewer", UUID.class);
            method.setAccessible(true);
            method.invoke(null, viewerId);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/invoke-unsubscribe-viewer", rootCause(exception));
        }
    }

    private static void clearSessionLocale() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/clear-session-locale", exception);
        }
    }

    private static void clearViewerState() {
        viewersBySession().clear();
        sessionByViewer().clear();
    }

    private static void setSessionLocale(UUID sessionId, String locale) {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.put(sessionId, locale);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/set-session-locale", exception);
        }
    }

    private static boolean hasSessionLocale(UUID sessionId) {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            return localeMap.containsKey(sessionId);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/has-session-locale", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, java.util.Set<UUID>> viewersBySession() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("VIEWERS_BY_SESSION");
            field.setAccessible(true);
            return (Map<UUID, java.util.Set<UUID>>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/viewers-by-session", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> sessionByViewer() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_BY_VIEWER");
            field.setAccessible(true);
            return (Map<UUID, UUID>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/session-by-viewer", exception);
        }
    }

    private static ServerPlayer fakePlayer(UUID playerId, String playerName) {
        try {
            Unsafe unsafe = unsafe();
            ServerPlayer player = (ServerPlayer) unsafe.allocateInstance(ServerPlayer.class);
            setObjectField(unsafe, Class.forName("net.minecraft.world.entity.Entity"), player, "uuid", playerId);
            setObjectField(unsafe, Class.forName("net.minecraft.world.entity.player.Player"),
                    player, "gameProfile", new GameProfile(playerId, playerName));
            return player;
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/fake-player", exception);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/unsafe", exception);
        }
    }

    private static void setObjectField(Unsafe unsafe, Class<?> owner, Object target, String fieldName, Object value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/set-field-" + fieldName, exception);
        }
    }

    private static Proposal proposal(String proposalId, String toolName, String argsJson) {
        return new Proposal(
                proposalId,
                RiskLevel.SAFE_MUTATION,
                "summary-" + proposalId,
                new ToolCall(toolName, argsJson),
                1_700_000_000_000L,
                new ProposalDetails("action", "minecraft:stick", 1L, List.of(), "note")
        );
    }

    private static TerminalBinding binding(String side) {
        return new TerminalBinding("minecraft:overworld", 1, 64, 1, Optional.ofNullable(side));
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
            throw new AssertionError("task7/proposal-lifecycle-behavior/read-protocol-version", rootCause(exception));
        }
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task7/proposal-lifecycle-behavior/bootstrap", exception);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
