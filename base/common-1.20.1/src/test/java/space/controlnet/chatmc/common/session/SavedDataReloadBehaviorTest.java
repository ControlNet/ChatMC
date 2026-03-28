package space.controlnet.chatmc.common.session;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.DecisionLogEntry;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionMetadata;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.SessionVisibility;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SavedDataReloadBehaviorTest {
    private static final UUID THINKING_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001111");
    private static final UUID EXECUTING_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000002222");
    private static final UUID WAIT_APPROVAL_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000003333");
    private static final UUID ROUNDTRIP_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000004444");
    private static final UUID OVERSIZE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000005555");

    private static final UUID THINKING_SESSION = UUID.fromString("10000000-0000-0000-0000-000000001111");
    private static final UUID EXECUTING_SESSION = UUID.fromString("10000000-0000-0000-0000-000000002222");
    private static final UUID WAIT_APPROVAL_SESSION = UUID.fromString("10000000-0000-0000-0000-000000003333");
    private static final UUID ROUNDTRIP_SESSION = UUID.fromString("10000000-0000-0000-0000-000000004444");
    private static final UUID OVERSIZE_SESSION = UUID.fromString("10000000-0000-0000-0000-000000005555");
    private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;

    @BeforeAll
    static void bootstrapMinecraft() {
        ensureMinecraftBootstrap();
    }

    @BeforeEach
    void resetSessionManager() {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
    }

    @Test
    void task11_savedDataReload_normalizesTransientStatesAndResumesActiveMapping() {
        SessionSnapshot thinking = snapshot(
                THINKING_SESSION,
                THINKING_PLAYER,
                "thinking-owner",
                SessionState.THINKING,
                Optional.empty(),
                Optional.empty(),
                chatHistory("thinking", 1L),
                List.of(decision("thinking-proposal", THINKING_PLAYER, "thinking-owner"))
        );

        Proposal executingProposal = proposal("executing-proposal", "{\"flow\":\"execute\"}");
        SessionSnapshot executing = snapshot(
                EXECUTING_SESSION,
                EXECUTING_PLAYER,
                "executing-owner",
                SessionState.EXECUTING,
                Optional.of(executingProposal),
                Optional.of(binding()),
                chatHistory("executing", 10L),
                List.of(decision(executingProposal.id(), EXECUTING_PLAYER, "executing-owner"))
        );

        Proposal waitProposal = proposal("wait-proposal", "{\"flow\":\"wait\"}");
        SessionSnapshot waitApproval = snapshot(
                WAIT_APPROVAL_SESSION,
                WAIT_APPROVAL_PLAYER,
                "wait-owner",
                SessionState.WAIT_APPROVAL,
                Optional.of(waitProposal),
                Optional.of(binding()),
                chatHistory("waiting", 20L),
                List.of(decision(waitProposal.id(), WAIT_APPROVAL_PLAYER, "wait-owner"))
        );

        PersistedSessions persisted = new PersistedSessions(1,
                List.of(thinking, executing, waitApproval),
                Map.of(
                        THINKING_PLAYER, THINKING_SESSION,
                        EXECUTING_PLAYER, EXECUTING_SESSION,
                        WAIT_APPROVAL_PLAYER, WAIT_APPROVAL_SESSION
                )
        );
        loadFromSavedData(persisted);

        SessionSnapshot loadedThinking = requireSnapshot("task11/saved-data-reload/thinking", THINKING_SESSION);
        assertEquals("task11/saved-data-reload/thinking-state", SessionState.IDLE, loadedThinking.state());
        assertFalse("task11/saved-data-reload/thinking-proposal", loadedThinking.pendingProposal().isPresent());

        SessionSnapshot loadedExecuting = requireSnapshot("task11/saved-data-reload/executing", EXECUTING_SESSION);
        assertEquals("task11/saved-data-reload/executing-state", SessionState.WAIT_APPROVAL, loadedExecuting.state());
        assertTrue("task11/saved-data-reload/executing-proposal", loadedExecuting.pendingProposal().isPresent());
        assertEquals("task11/saved-data-reload/executing-proposal-id", executingProposal.id(),
                loadedExecuting.pendingProposal().orElseThrow().id());

        SessionSnapshot loadedWaiting = requireSnapshot("task11/saved-data-reload/wait-approval", WAIT_APPROVAL_SESSION);
        assertEquals("task11/saved-data-reload/wait-approval-state", SessionState.WAIT_APPROVAL, loadedWaiting.state());
        assertEquals("task11/saved-data-reload/wait-approval-proposal", waitProposal.id(),
                loadedWaiting.pendingProposal().orElseThrow().id());

        assertEquals("task11/saved-data-reload/active-thinking", THINKING_SESSION,
                requireActive("task11/saved-data-reload/active-thinking", THINKING_PLAYER));
        assertEquals("task11/saved-data-reload/active-executing", EXECUTING_SESSION,
                requireActive("task11/saved-data-reload/active-executing", EXECUTING_PLAYER));
        assertEquals("task11/saved-data-reload/active-wait", WAIT_APPROVAL_SESSION,
                requireActive("task11/saved-data-reload/active-wait", WAIT_APPROVAL_PLAYER));
    }

    @Test
    void task11_savedDataReload_roundTripKeepsMessagesAndDecisions() {
        List<ChatMessage> history = chatHistory("roundtrip", 100L);
        List<DecisionLogEntry> decisions = List.of(decision("roundtrip-proposal", ROUNDTRIP_PLAYER, "roundtrip-owner"));
        SessionSnapshot snapshot = snapshot(
                ROUNDTRIP_SESSION,
                ROUNDTRIP_PLAYER,
                "roundtrip-owner",
                SessionState.DONE,
                Optional.empty(),
                Optional.empty(),
                history,
                decisions
        );
        loadFromSavedData(new PersistedSessions(1, List.of(snapshot), Map.of()));

        SessionSnapshot loaded = requireSnapshot("task11/saved-data-reload/roundtrip", ROUNDTRIP_SESSION);
        assertEquals("task11/saved-data-reload/roundtrip-messages", history, loaded.messages());
        assertEquals("task11/saved-data-reload/roundtrip-decisions", decisions, loaded.decisions());
    }

    @Test
    void task11_savedDataReload_rejectsOversizedToolArgsOnDeserialize() {
        SessionSnapshot snapshot = snapshot(
                OVERSIZE_SESSION,
                OVERSIZE_PLAYER,
                "oversize-owner",
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal("oversize-proposal", repeat('x', MAX_TOOL_ARGS_JSON_LENGTH))),
                Optional.of(binding()),
                List.of(),
                List.of(decision("oversize-proposal", OVERSIZE_PLAYER, "oversize-owner"))
        );
        PersistedSessions persisted = new PersistedSessions(1, List.of(snapshot), Map.of(OVERSIZE_PLAYER, OVERSIZE_SESSION));
        CompoundTag root = serialize(persisted);
        ListTag sessions = root.getList("sessions", Tag.TAG_COMPOUND);
        CompoundTag session = sessions.getCompound(0);
        CompoundTag proposal = session.getCompound("proposal");
        proposal.putString("argsJson", repeat('x', MAX_TOOL_ARGS_JSON_LENGTH + 1));

        IllegalArgumentException thrown = expectIllegalArgument(
                "task11/saved-data-reload/oversize-args",
                () -> ChatMCSessionsSavedData.load(root)
        );
        assertContains("task11/saved-data-reload/oversize-args-signal", thrown.getMessage(), "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE");
        assertContains("task11/saved-data-reload/oversize-args-phase", thrown.getMessage(), "phase='read'");
    }

    private static SessionSnapshot snapshot(
            UUID sessionId,
            UUID ownerId,
            String ownerName,
            SessionState state,
            Optional<Proposal> proposal,
            Optional<TerminalBinding> binding,
            List<ChatMessage> messages,
            List<DecisionLogEntry> decisions
    ) {
        return new SessionSnapshot(
                new SessionMetadata(sessionId, ownerId, ownerName, SessionVisibility.PRIVATE, Optional.empty(), ownerName + "-session", 100L, 200L),
                messages,
                state,
                proposal,
                binding,
                decisions,
                Optional.empty()
        );
    }

    private static List<ChatMessage> chatHistory(String prefix, long baseTimestamp) {
        return List.of(
                ChatMessage.user(prefix + "-user", baseTimestamp),
                ChatMessage.assistant(prefix + "-assistant", baseTimestamp + 1)
        );
    }

    private static DecisionLogEntry decision(String proposalId, UUID playerId, String playerName) {
        return new DecisionLogEntry(
                1_600_000_000L,
                Optional.of(playerId),
                Optional.of(playerName),
                proposalId,
                Optional.of("chatmc.tool"),
                ApprovalDecision.APPROVE
        );
    }

    private static Proposal proposal(String id, String argsJson) {
        return new Proposal(
                id,
                RiskLevel.SAFE_MUTATION,
                id + "-summary",
                new ToolCall("chatmc.tool", argsJson),
                1_700_000_000L,
                new ProposalDetails("action", "minecraft:stick", 1L, List.of(id + "-missing"), "note")
        );
    }

    private static TerminalBinding binding() {
        return new TerminalBinding("minecraft:overworld", 10, 64, 10, Optional.of("north"));
    }

    private static void loadFromSavedData(PersistedSessions persisted) {
        CompoundTag root = serialize(persisted);
        PersistedSessions rehydrated = deserialize(root);
        ChatMCNetwork.SESSIONS.loadFromSave(rehydrated);
    }

    private static CompoundTag serialize(PersistedSessions persisted) {
        ChatMCSessionsSavedData savedData = new ChatMCSessionsSavedData();
        savedData.setData(persisted);
        CompoundTag root = new CompoundTag();
        savedData.save(root);
        return root;
    }

    private static PersistedSessions deserialize(CompoundTag root) {
        return ChatMCSessionsSavedData.load(root).data();
    }

    private static SessionSnapshot requireSnapshot(String assertionName, UUID sessionId) {
        return ChatMCNetwork.SESSIONS.get(sessionId)
                .orElseThrow(() -> new AssertionError(assertionName + " -> missing session " + sessionId));
    }

    private static UUID requireActive(String assertionName, UUID playerId) {
        return ChatMCNetwork.SESSIONS.getActiveSessionId(playerId)
                .orElseThrow(() -> new AssertionError(assertionName + " -> missing active session for " + playerId));
    }

    private static IllegalArgumentException expectIllegalArgument(String assertionName, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException exception) {
            return exception;
        } catch (Throwable throwable) {
            throw new AssertionError(assertionName + " -> unexpected exception", throwable);
        }
        throw new AssertionError(assertionName + " -> expected IllegalArgumentException");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (!value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task11/saved-data-reload/bootstrap", exception);
        }
    }
}
