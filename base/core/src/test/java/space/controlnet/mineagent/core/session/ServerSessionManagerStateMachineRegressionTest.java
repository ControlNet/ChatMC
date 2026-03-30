package space.controlnet.mineagent.core.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.ApprovalDecision;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ServerSessionManagerStateMachineRegressionTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final String PLAYER_NAME = "task12-player";

    @BeforeEach
    void configureSessionBounds() {
        System.setProperty("mineagent.maxMessagesPerSession", "3");
        System.setProperty("mineagent.maxDecisionsPerSession", "2");
        System.setProperty("mineagent.maxMessageLength", "16");
    }

    @Test
    void task12_stateMachine_legalAndIllegalTransitions_areDeterministic() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        Proposal proposal = proposal("proposal-legal-1", "mc.craft", "{\"item\":\"stick\"}");
        TerminalBinding binding = binding("north");

        assertFalse("task12/state-machine/illegal-idle-rejects-proposal",
                manager.trySetProposal(sessionId, proposal, binding));
        assertState("task12/state-machine/illegal-idle-keeps-idle", manager, sessionId, SessionState.IDLE);

        assertTrue("task12/state-machine/legal-idle-to-thinking", manager.tryStartThinking(sessionId));
        assertState("task12/state-machine/legal-after-start-thinking", manager, sessionId, SessionState.THINKING);

        assertFalse("task12/state-machine/illegal-thinking-reentry",
                manager.tryStartThinking(sessionId));
        assertState("task12/state-machine/illegal-thinking-reentry-state", manager, sessionId, SessionState.THINKING);

        assertTrue("task12/state-machine/legal-thinking-to-wait-approval",
                manager.trySetProposal(sessionId, proposal, binding));
        SessionSnapshot waitApproval = requireSnapshot("task12/state-machine/wait-approval-snapshot", manager, sessionId);
        assertEquals("task12/state-machine/wait-approval-state", SessionState.WAIT_APPROVAL, waitApproval.state());
        assertEquals("task12/state-machine/wait-approval-proposal-id",
                proposal.id(), waitApproval.pendingProposal().orElseThrow().id());
        assertEquals("task12/state-machine/wait-approval-binding-side",
                Optional.of("north"), waitApproval.proposalBinding().orElseThrow().side());

        Proposal replacement = proposal("proposal-replacement", "mc.move", "{\"count\":1}");
        assertFalse("task12/state-machine/illegal-wait-approval-rejects-second-proposal",
                manager.trySetProposal(sessionId, replacement, binding("south")));
        SessionSnapshot stillWaiting = requireSnapshot("task12/state-machine/still-waiting", manager, sessionId);
        assertEquals("task12/state-machine/still-waiting-original-proposal-kept",
                proposal.id(), stillWaiting.pendingProposal().orElseThrow().id());

        assertFalse("task12/state-machine/illegal-wait-approval-wrong-id-start-executing",
                manager.tryStartExecuting(sessionId, "proposal-wrong"));
        assertState("task12/state-machine/illegal-wrong-id-keeps-wait", manager, sessionId, SessionState.WAIT_APPROVAL);

        assertTrue("task12/state-machine/legal-wait-approval-to-executing",
                manager.tryStartExecuting(sessionId, proposal.id()));
        assertState("task12/state-machine/legal-state-executing", manager, sessionId, SessionState.EXECUTING);

        assertFalse("task12/state-machine/illegal-executing-fail-wrong-id",
                manager.tryFailProposal(sessionId, "proposal-wrong", "tool failure"));
        assertState("task12/state-machine/illegal-fail-wrong-id-keeps-executing",
                manager, sessionId, SessionState.EXECUTING);

        assertTrue("task12/state-machine/legal-executing-to-failed",
                manager.tryFailProposal(sessionId, proposal.id(), "tool failure"));
        SessionSnapshot failed = requireSnapshot("task12/state-machine/failed-snapshot", manager, sessionId);
        assertEquals("task12/state-machine/failed-state", SessionState.FAILED, failed.state());
        assertTrue("task12/state-machine/failed-clears-pending-proposal", failed.pendingProposal().isEmpty());
        assertTrue("task12/state-machine/failed-clears-proposal-binding", failed.proposalBinding().isEmpty());
        assertEquals("task12/state-machine/failed-error-message",
                Optional.of("tool failure"), failed.lastError());
    }

    @Test
    void task12_proposalLifecycle_firstPassPath_transitionsWithoutSilentDrop() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        Proposal proposal = proposal("proposal-first-pass", "mc.deposit", "{\"slot\":4}");
        TerminalBinding binding = binding("east");

        assertTrue("task12/proposal-lifecycle/first-pass-start-thinking",
                manager.tryStartThinking(sessionId));
        assertTrue("task12/proposal-lifecycle/first-pass-set-proposal-from-thinking",
                manager.trySetProposal(sessionId, proposal, binding));
        SessionSnapshot waiting = requireSnapshot("task12/proposal-lifecycle/first-pass-waiting-snapshot", manager, sessionId);
        assertEquals("task12/proposal-lifecycle/first-pass-state-wait-approval",
                SessionState.WAIT_APPROVAL, waiting.state());
        assertTrue("task12/proposal-lifecycle/first-pass-no-idle-fallback",
                waiting.state() != SessionState.IDLE);

        assertTrue("task12/proposal-lifecycle/first-pass-start-executing",
                manager.tryStartExecuting(sessionId, proposal.id()));
        assertTrue("task12/proposal-lifecycle/first-pass-resolve-execution",
                manager.tryResolveExecution(sessionId, proposal.id(), "done", SessionState.DONE));

        SessionSnapshot done = requireSnapshot("task12/proposal-lifecycle/first-pass-done-snapshot", manager, sessionId);
        assertEquals("task12/proposal-lifecycle/first-pass-final-state", SessionState.DONE, done.state());
        assertTrue("task12/proposal-lifecycle/first-pass-clears-proposal", done.pendingProposal().isEmpty());
        assertTrue("task12/proposal-lifecycle/first-pass-clears-binding", done.proposalBinding().isEmpty());
        assertEquals("task12/proposal-lifecycle/first-pass-appended-message-count", 1, done.messages().size());
        assertEquals("task12/proposal-lifecycle/first-pass-appended-role",
                ChatRole.ASSISTANT, done.messages().get(0).role());
        assertEquals("task12/proposal-lifecycle/first-pass-appended-text", "done", done.messages().get(0).text());
    }

    @Test
    void task12_proposalLifecycle_approvalResumeInteraction_staysCompatible() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        Proposal firstProposal = proposal("proposal-resume-1", "mc.deposit", "{\"slot\":1}");
        Proposal resumedProposal = proposal("proposal-resume-2", "mc.deposit", "{\"slot\":2}");

        assertTrue("task12/approval-resume/start-thinking",
                manager.tryStartThinking(sessionId));
        assertTrue("task12/approval-resume/set-first-proposal",
                manager.trySetProposal(sessionId, firstProposal, binding("up")));
        assertTrue("task12/approval-resume/start-executing-first-proposal",
                manager.tryStartExecuting(sessionId, firstProposal.id()));

        SessionSnapshot executingWithPending = requireSnapshot(
                "task12/approval-resume/executing-with-pending", manager, sessionId);
        assertEquals("task12/approval-resume/executing-state-before-clear",
                SessionState.EXECUTING, executingWithPending.state());
        assertEquals("task12/approval-resume/first-proposal-still-pending",
                firstProposal.id(), executingWithPending.pendingProposal().orElseThrow().id());

        manager.clearProposalPreserveState(sessionId);
        SessionSnapshot executingCleared = requireSnapshot(
                "task12/approval-resume/executing-cleared", manager, sessionId);
        assertEquals("task12/approval-resume/clear-preserve-state-keeps-executing",
                SessionState.EXECUTING, executingCleared.state());
        assertTrue("task12/approval-resume/clear-preserve-state-clears-pending",
                executingCleared.pendingProposal().isEmpty());
        assertTrue("task12/approval-resume/clear-preserve-state-clears-binding",
                executingCleared.proposalBinding().isEmpty());

        assertTrue("task12/approval-resume/reproposal-from-executing-accepted",
                manager.trySetProposal(sessionId, resumedProposal, binding("south")));

        SessionSnapshot resumedWaiting = requireSnapshot(
                "task12/approval-resume/resumed-waiting", manager, sessionId);
        assertEquals("task12/approval-resume/resumed-state-wait-approval",
                SessionState.WAIT_APPROVAL, resumedWaiting.state());
        assertEquals("task12/approval-resume/resumed-proposal-id",
                resumedProposal.id(), resumedWaiting.pendingProposal().orElseThrow().id());
        assertEquals("task12/approval-resume/resumed-binding-side",
                Optional.of("south"), resumedWaiting.proposalBinding().orElseThrow().side());
    }

    @Test
    void task12_loadNormalization_recoversTransientStates_andNormalizesBounds() {
        ServerSessionManager manager = new ServerSessionManager();

        UUID ownerOne = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID ownerTwo = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID ownerThree = UUID.fromString("00000000-0000-0000-0000-000000000203");
        UUID ownerFour = UUID.fromString("00000000-0000-0000-0000-000000000204");

        UUID sessionThinkingWithoutProposalId = UUID.fromString("00000000-0000-0000-0000-000000001201");
        UUID sessionThinkingWithProposalId = UUID.fromString("00000000-0000-0000-0000-000000001202");
        UUID sessionExecutingWithoutProposalId = UUID.fromString("00000000-0000-0000-0000-000000001203");
        UUID sessionExecutingWithProposalId = UUID.fromString("00000000-0000-0000-0000-000000001204");

        Proposal pendingProposal = proposal("proposal-normalize-thinking", "mc.craft", "{\"count\":1}");
        Proposal executingProposal = proposal("proposal-normalize-executing", "mc.move", "{\"count\":2}");

        SessionSnapshot thinkingWithoutProposal = snapshot(
                sessionThinkingWithoutProposalId,
                ownerOne,
                SessionState.THINKING,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty()
        );

        List<ChatMessage> oversizedMessages = List.of(
                new ChatMessage(ChatRole.USER, "drop-oldest", 1L),
                new ChatMessage(ChatRole.USER, "drop-second", 2L),
                new ChatMessage(ChatRole.USER, null, 3L),
                new ChatMessage(ChatRole.ASSISTANT, "12345678901234567890", 4L),
                new ChatMessage(ChatRole.ASSISTANT, "tail", 5L)
        );
        List<DecisionLogEntry> oversizedDecisions = List.of(
                decision("decision-old", ApprovalDecision.DENY, 1L),
                decision("decision-mid", ApprovalDecision.APPROVE, 2L),
                decision("decision-new", ApprovalDecision.DENY, 3L)
        );

        SessionSnapshot thinkingWithProposal = snapshot(
                sessionThinkingWithProposalId,
                ownerTwo,
                SessionState.THINKING,
                Optional.of(pendingProposal),
                Optional.of(binding("west")),
                oversizedMessages,
                oversizedDecisions,
                Optional.of("keep-error")
        );

        SessionSnapshot executingWithoutProposal = snapshot(
                sessionExecutingWithoutProposalId,
                ownerThree,
                SessionState.EXECUTING,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty()
        );

        SessionSnapshot executingWithProposal = snapshot(
                sessionExecutingWithProposalId,
                ownerFour,
                SessionState.EXECUTING,
                Optional.of(executingProposal),
                Optional.of(binding("north")),
                List.of(),
                List.of(),
                Optional.empty()
        );

        PersistedSessions persisted = new PersistedSessions(
                1,
                List.of(
                        thinkingWithoutProposal,
                        thinkingWithProposal,
                        executingWithoutProposal,
                        executingWithProposal
                ),
                Map.of(ownerOne, sessionThinkingWithoutProposalId)
        );

        manager.loadFromSave(persisted);

        SessionSnapshot loadedThinkingWithoutProposal = requireSnapshot(
                "task12/load-normalization/thinking-without-proposal", manager, sessionThinkingWithoutProposalId);
        assertEquals("task12/load-normalization/thinking-without-proposal-normalized-to-idle",
                SessionState.IDLE, loadedThinkingWithoutProposal.state());

        SessionSnapshot loadedThinkingWithProposal = requireSnapshot(
                "task12/load-normalization/thinking-with-proposal", manager, sessionThinkingWithProposalId);
        assertEquals("task12/load-normalization/thinking-with-proposal-normalized-to-wait",
                SessionState.WAIT_APPROVAL, loadedThinkingWithProposal.state());
        assertEquals("task12/load-normalization/thinking-with-proposal-id-preserved",
                pendingProposal.id(), loadedThinkingWithProposal.pendingProposal().orElseThrow().id());

        assertEquals("task12/load-normalization/messages-trimmed-to-max",
                3, loadedThinkingWithProposal.messages().size());
        assertEquals("task12/load-normalization/messages-null-text-normalized",
                "", loadedThinkingWithProposal.messages().get(0).text());
        assertEquals("task12/load-normalization/messages-long-text-trimmed",
                "1234567890123456", loadedThinkingWithProposal.messages().get(1).text());
        assertEquals("task12/load-normalization/messages-tail-kept",
                "tail", loadedThinkingWithProposal.messages().get(2).text());

        assertEquals("task12/load-normalization/decisions-trimmed-to-max",
                2, loadedThinkingWithProposal.decisions().size());
        assertEquals("task12/load-normalization/decisions-oldest-dropped",
                "decision-mid", loadedThinkingWithProposal.decisions().get(0).proposalId());
        assertEquals("task12/load-normalization/decisions-newest-kept",
                "decision-new", loadedThinkingWithProposal.decisions().get(1).proposalId());

        SessionSnapshot loadedExecutingWithoutProposal = requireSnapshot(
                "task12/load-normalization/executing-without-proposal", manager, sessionExecutingWithoutProposalId);
        assertEquals("task12/load-normalization/executing-without-proposal-normalized-to-idle",
                SessionState.IDLE, loadedExecutingWithoutProposal.state());

        SessionSnapshot loadedExecutingWithProposal = requireSnapshot(
                "task12/load-normalization/executing-with-proposal", manager, sessionExecutingWithProposalId);
        assertEquals("task12/load-normalization/executing-with-proposal-normalized-to-wait",
                SessionState.WAIT_APPROVAL, loadedExecutingWithProposal.state());
        assertEquals("task12/load-normalization/executing-with-proposal-id-preserved",
                executingProposal.id(), loadedExecutingWithProposal.pendingProposal().orElseThrow().id());
    }

    @Test
    void task12_activeLifecycle_deleteClearsOwnerMappingAndGetActiveRecreatesSession() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionSnapshot created = manager.create(PLAYER_ID, PLAYER_NAME);
        UUID deletedSessionId = created.metadata().sessionId();

        assertEquals("task12/active-lifecycle/active-before-delete",
                Optional.of(deletedSessionId), manager.getActiveSessionId(PLAYER_ID));

        manager.delete(deletedSessionId);

        assertTrue("task12/active-lifecycle/deleted-session-removed", manager.get(deletedSessionId).isEmpty());
        assertEquals("task12/active-lifecycle/active-cleared-after-delete",
                Optional.empty(), manager.getActiveSessionId(PLAYER_ID));

        SessionSnapshot recreated = manager.getActive(PLAYER_ID, PLAYER_NAME);
        assertTrue("task12/active-lifecycle/recreated-session-new-id",
                !recreated.metadata().sessionId().equals(deletedSessionId));
        assertEquals("task12/active-lifecycle/recreated-active-id",
                Optional.of(recreated.metadata().sessionId()), manager.getActiveSessionId(PLAYER_ID));
    }

    @Test
    void task12_missingSessionMutators_returnFalseWithoutCreatingSyntheticSessions() {
        ServerSessionManager manager = new ServerSessionManager();
        UUID missingSessionId = UUID.fromString("00000000-0000-0000-0000-000000001299");
        Proposal proposal = proposal("proposal-missing-session", "mc.craft", "{\"item\":\"minecraft:stick\"}");

        assertFalse("task12/missing-session/start-thinking", manager.tryStartThinking(missingSessionId));
        assertFalse("task12/missing-session/set-proposal", manager.trySetProposal(missingSessionId, proposal, binding("north")));
        assertFalse("task12/missing-session/start-executing", manager.tryStartExecuting(missingSessionId, proposal.id()));
        assertFalse("task12/missing-session/fail-proposal", manager.tryFailProposal(missingSessionId, proposal.id(), "boom"));
        assertFalse("task12/missing-session/resolve-execution",
                manager.tryResolveExecution(missingSessionId, proposal.id(), "done", SessionState.DONE));
        assertTrue("task12/missing-session/no-synthetic-session-created", manager.get(missingSessionId).isEmpty());

        manager.appendMessage(missingSessionId, new ChatMessage(ChatRole.USER, "hello", 1L));
        manager.appendDecision(missingSessionId, decision(proposal.id(), ApprovalDecision.DENY, 2L));
        manager.setState(missingSessionId, SessionState.DONE);
        manager.setProposal(missingSessionId, proposal, binding("north"));
        manager.clearProposal(missingSessionId);
        manager.clearProposalPreserveState(missingSessionId);
        manager.setError(missingSessionId, "boom");
        manager.rename(missingSessionId, "Renamed");
        manager.setVisibility(missingSessionId, SessionVisibility.PUBLIC, Optional.empty());

        assertTrue("task12/missing-session/no-synthetic-session-created-after-direct-mutators",
                manager.get(missingSessionId).isEmpty());
    }

    @Test
    void task12_loadNormalization_filtersStaleAndMismatchedActiveMappings() {
        ServerSessionManager manager = new ServerSessionManager();
        UUID validOwner = UUID.fromString("00000000-0000-0000-0000-000000000221");
        UUID staleOwner = UUID.fromString("00000000-0000-0000-0000-000000000222");
        UUID mismatchedOwner = UUID.fromString("00000000-0000-0000-0000-000000000223");
        UUID snapshotOwner = UUID.fromString("00000000-0000-0000-0000-000000000224");
        UUID validSessionId = UUID.fromString("00000000-0000-0000-0000-000000001221");
        UUID mismatchedSessionId = UUID.fromString("00000000-0000-0000-0000-000000001222");

        PersistedSessions persisted = new PersistedSessions(
                1,
                List.of(
                        snapshot(validSessionId, validOwner, SessionState.IDLE, Optional.empty(), Optional.empty(),
                                List.of(), List.of(), Optional.empty()),
                        snapshot(mismatchedSessionId, snapshotOwner, SessionState.IDLE, Optional.empty(), Optional.empty(),
                                List.of(), List.of(), Optional.empty())
                ),
                Map.of(
                        validOwner, validSessionId,
                        staleOwner, UUID.fromString("00000000-0000-0000-0000-000000001223"),
                        mismatchedOwner, mismatchedSessionId
                )
        );

        manager.loadFromSave(persisted);

        assertEquals("task12/load-normalization/valid-active-mapping-kept",
                Optional.of(validSessionId), manager.getActiveSessionId(validOwner));
        assertEquals("task12/load-normalization/stale-active-mapping-dropped",
                Optional.empty(), manager.getActiveSessionId(staleOwner));
        assertEquals("task12/load-normalization/mismatched-active-mapping-dropped",
                Optional.empty(), manager.getActiveSessionId(mismatchedOwner));
    }

    private SessionContext newSessionContext() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionSnapshot snapshot = manager.create(PLAYER_ID, PLAYER_NAME);
        return new SessionContext(manager, snapshot.metadata().sessionId());
    }

    private static Proposal proposal(String proposalId, String toolName, String argsJson) {
        return new Proposal(
                proposalId,
                RiskLevel.SAFE_MUTATION,
                "proposal-summary-" + proposalId,
                new ToolCall(toolName, argsJson),
                1_700_000_000_000L,
                new ProposalDetails("action", "minecraft:stick", 1L, List.of("minecraft:plank"), "note")
        );
    }

    private static TerminalBinding binding(String side) {
        return new TerminalBinding("minecraft:overworld", 10, 64, 10, Optional.ofNullable(side));
    }

    private static SessionSnapshot snapshot(
            UUID sessionId,
            UUID ownerId,
            SessionState state,
            Optional<Proposal> pendingProposal,
            Optional<TerminalBinding> proposalBinding,
            List<ChatMessage> messages,
            List<DecisionLogEntry> decisions,
            Optional<String> lastError
    ) {
        SessionMetadata metadata = new SessionMetadata(
                sessionId,
                ownerId,
                "owner-" + ownerId,
                SessionVisibility.PRIVATE,
                Optional.empty(),
                "Session " + sessionId,
                100L,
                200L
        );
        return new SessionSnapshot(
                metadata,
                messages,
                state,
                pendingProposal,
                proposalBinding,
                decisions,
                lastError
        );
    }

    private static DecisionLogEntry decision(String proposalId, ApprovalDecision decision, long timestampMillis) {
        return new DecisionLogEntry(
                timestampMillis,
                Optional.empty(),
                Optional.of("tester"),
                proposalId,
                Optional.of("mc.tool"),
                decision
        );
    }

    private static SessionSnapshot requireSnapshot(String assertionName, ServerSessionManager manager, UUID sessionId) {
        return manager.get(sessionId).orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
    }

    private static void assertState(String assertionName, ServerSessionManager manager, UUID sessionId, SessionState expected) {
        SessionSnapshot snapshot = requireSnapshot(assertionName, manager, sessionId);
        assertEquals(assertionName, expected, snapshot.state());
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (!value) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (value) {
            throw new AssertionError(assertionName + " -> expected false");
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private record SessionContext(ServerSessionManager manager, UUID sessionId) {
    }
}
