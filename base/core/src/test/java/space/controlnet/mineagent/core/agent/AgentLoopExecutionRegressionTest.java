package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.audit.AuditEvent;
import space.controlnet.mineagent.core.audit.AuditLogger;
import space.controlnet.mineagent.core.audit.LlmAuditEvent;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.ChatRole;
import space.controlnet.mineagent.core.session.ServerSessionManager;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentLoopExecutionRegressionTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000551");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000001551");

    @Test
    void task22_agentLoop_constructorAndSetters_areOperational() {
        AgentLoop loop = new AgentLoop(new NoopAuditLogger(), (message, exception) -> {
        });

        loop.setRateLimitCooldown(250L);
        loop.setTimeoutMs(500L);
        loop.setMaxToolCalls(7);
        loop.setMaxIterations(4);
        loop.setMaxIterations(0);
        loop.setLogResponses(true);
        loop.setMaxRetries(2);

        assertTrue("task22/constructor-and-setters/reached", true);
    }

    @Test
    void task22_agentLoop_routeHelpers_coverTerminalAndActionBranches() {
        AgentLoop loop = new AgentLoop(new NoopAuditLogger(), (message, exception) -> {
        });

        String routeTerminal = (String) invokePrivate(loop, "routeAfterReason", state(Map.of("resultError", "boom")));
        String routeToolCall = (String) invokePrivate(loop, "routeAfterReason", state(Map.of("decisionAction", "TOOL_CALL")));
        String routeRespond = (String) invokePrivate(loop, "routeAfterReason", state(Map.of("decisionAction", "RESPOND")));
        String routeBlank = (String) invokePrivate(loop, "routeAfterReason", state(Map.of("decisionAction", "   ")));

        String afterExecuteTerminal = (String) invokePrivate(loop, "routeAfterExecute", state(Map.of("resultProposal", true)));
        String afterExecuteContinue = (String) invokePrivate(loop, "routeAfterExecute", state(Map.of()));

        assertEquals("task22/route/terminal", "end", routeTerminal);
        assertEquals("task22/route/tool-call", "execute", routeToolCall);
        assertEquals("task22/route/respond", "respond", routeRespond);
        assertEquals("task22/route/blank", "end", routeBlank);
        assertEquals("task22/route-after-execute/terminal", "end", afterExecuteTerminal);
        assertEquals("task22/route-after-execute/continue", "reason", afterExecuteContinue);
    }

    @Test
    void task22_agentLoop_reasonExecuteRespond_andRunLoop_coverDeterministicPaths() {
        LlmRuntime.clear();
        AgentLoop loop = new AgentLoop(new NoopAuditLogger(), (message, exception) -> {
        });
        RecordingSessionContext context = new RecordingSessionContext();
        AgentPlayerContext player = new RecordingPlayerContext(PLAYER_ID, "task22-player");

        putActive(loop, "activePlayers", SESSION_ID, player);
        putActive(loop, "activeContexts", SESSION_ID, context);

        loop.setMaxIterations(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> maxed = (Map<String, Object>) invokePrivate(
                loop,
                "reasonNode",
                state(Map.of("sessionId", SESSION_ID, "locale", "en_us", "iteration", 1))
        );
        assertEquals("task22/reason/max-iterations-error", "Max iterations reached", maxed.get("resultError"));

        loop.setMaxIterations(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> llmUnavailable = (Map<String, Object>) invokePrivate(
                loop,
                "reasonNode",
                state(Map.of("sessionId", SESSION_ID, "locale", "en_us", "iteration", 0))
        );
        assertEquals("task22/reason/no-model-error", "LLM failed to produce a decision", llmUnavailable.get("resultError"));

        @SuppressWarnings("unchecked")
        Map<String, Object> invalidExecute = (Map<String, Object>) invokePrivate(
                loop,
                "executeNode",
                state(Map.of("sessionId", SESSION_ID, "iteration", 2))
        );
        assertEquals("task22/execute/invalid-state", "Invalid state for execution", invalidExecute.get("resultError"));

        Proposal proposal = new Proposal(
                "proposal-task22",
                RiskLevel.SAFE_MUTATION,
                "Do thing",
                new ToolCall("mc.move", "{}"),
                1L,
                ProposalDetails.empty()
        );
        context.nextOutcome = ToolOutcome.proposal(proposal);
        putActive(loop, "activeDecisions", SESSION_ID, AgentDecision.toolCall(new ToolCall("mc.move", "{}"), "thinking"));

        @SuppressWarnings("unchecked")
        Map<String, Object> proposalUpdate = (Map<String, Object>) invokePrivate(
                loop,
                "executeNode",
                state(Map.of("sessionId", SESSION_ID, "iteration", 3))
        );
        assertEquals("task22/execute/proposal-flag", Boolean.TRUE, proposalUpdate.get("resultProposal"));

        context.nextOutcome = ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
        putActive(loop, "activeDecisions", SESSION_ID, AgentDecision.toolCall(new ToolCall("mc.echo", "{}"), "thought"));

        @SuppressWarnings("unchecked")
        Map<String, Object> continueUpdate = (Map<String, Object>) invokePrivate(
                loop,
                "executeNode",
                state(Map.of("sessionId", SESSION_ID, "iteration", 4))
        );
        assertEquals("task22/execute/continue-iteration", 4, continueUpdate.get("iteration"));
        assertTrue("task22/execute/tool-message-appended", !context.appendedMessages.isEmpty());

        putActive(loop, "activeDecisions", SESSION_ID, AgentDecision.respond("   ", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> blankResponse = (Map<String, Object>) invokePrivate(
                loop,
                "respondNode",
                state(Map.of("sessionId", SESSION_ID, "iteration", 5))
        );
        assertEquals("task22/respond/blank-error", "No response in decision", blankResponse.get("resultError"));

        putActive(loop, "activeDecisions", SESSION_ID, AgentDecision.respond("done", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> responseUpdate = (Map<String, Object>) invokePrivate(
                loop,
                "respondNode",
                state(Map.of("sessionId", SESSION_ID, "iteration", 6))
        );
        assertEquals("task22/respond/success-payload", "done", responseUpdate.get("resultResponse"));

        AgentLoopResult loopResult = loop.runLoop(
                player,
                SESSION_ID,
                null,
                "en_us",
                context
        );
        assertTrue("task22/run-loop/error-on-unavailable-model", loopResult.hasError());

        assertFalse("task22/run-loop/active-player-cleaned", activeMap(loop, "activePlayers").containsKey(SESSION_ID));
        assertFalse("task22/run-loop/active-context-cleaned", activeMap(loop, "activeContexts").containsKey(SESSION_ID));
    }

    private static Object state(Map<String, Object> values) {
        try {
            Class<?> stateClass = Class.forName("space.controlnet.mineagent.core.agent.AgentLoop$LoopState");
            Constructor<?> constructor = stateClass.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new HashMap<>(values));
        } catch (Exception exception) {
            throw new AssertionError("task22/state/create", exception);
        }
    }

    private static Object invokePrivate(AgentLoop loop, String methodName, Object state) {
        try {
            Method method = AgentLoop.class.getDeclaredMethod(methodName, state.getClass());
            method.setAccessible(true);
            return method.invoke(loop, state);
        } catch (Exception exception) {
            throw new AssertionError("task22/invoke/" + methodName, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<UUID, Object> activeMap(AgentLoop loop, String fieldName) {
        try {
            Field field = AgentLoop.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ConcurrentHashMap<UUID, Object>) field.get(loop);
        } catch (Exception exception) {
            throw new AssertionError("task22/active-map/" + fieldName, exception);
        }
    }

    private static void putActive(AgentLoop loop, String fieldName, UUID sessionId, Object value) {
        activeMap(loop, fieldName).put(sessionId, value);
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

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class RecordingSessionContext implements AgentSessionContext {
        private final SessionSnapshot snapshot = new ServerSessionManager().create(PLAYER_ID, "task22-player");
        private final List<ChatMessage> appendedMessages = new ArrayList<>();
        private ToolOutcome nextOutcome = ToolOutcome.result(ToolResult.ok("{}"));

        @Override
        public Optional<SessionSnapshot> getSession(UUID sessionId) {
            return Optional.of(snapshot);
        }

        @Override
        public void appendMessage(UUID sessionId, ChatMessage message) {
            appendedMessages.add(message);
        }

        @Override
        public Optional<TerminalContext> getTerminal(AgentPlayerContext player) {
            return Optional.empty();
        }

        @Override
        public ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            return nextOutcome;
        }

        @Override
        public List<space.controlnet.mineagent.core.tools.AgentTool> getToolSpecs() {
            return List.of();
        }

        @Override
        public String renderPrompt(PromptId promptId, String locale, Map<String, String> variables) {
            return "{\"action\":\"respond\",\"response\":\"ok\"}";
        }

        @Override
        public void logDebug(String message, Object... args) {
        }

        @Override
        public void logError(String message, Throwable error) {
        }
    }

    private record RecordingPlayerContext(UUID playerId, String playerName) implements AgentPlayerContext {
        @Override
        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public String getPlayerName() {
            return playerName;
        }
    }

    private static final class NoopAuditLogger implements AuditLogger {
        @Override
        public void log(AuditEvent event) {
        }

        @Override
        public void logLlm(LlmAuditEvent event) {
        }
    }
}
