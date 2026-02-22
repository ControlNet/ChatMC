package space.controlnet.chatmc.common.network;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;

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
