package space.controlnet.chatmc.common.network;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.TerminalBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NetworkAgentErrorBehaviorTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000711");
    private static final String PLAYER_NAME = "task7-agent-error-behavior";

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
    void task7_agentErrorBehavior_nullResultFailsSessionAndAppendsDeterministicMessage() {
        UUID sessionId = newSession();

        invokeHandleAgentLoopResult(null, sessionId, null, "en_us");

        SessionSnapshot failed = requireSnapshot("task7/agent-error-behavior/null-result", sessionId);
        assertEquals("task7/agent-error-behavior/null-result-state", SessionState.FAILED, failed.state());
        assertEquals("task7/agent-error-behavior/null-result-error",
                Optional.of("Agent returned null result"), failed.lastError());
        assertEquals("task7/agent-error-behavior/null-result-role",
                ChatRole.ASSISTANT, failed.messages().get(failed.messages().size() - 1).role());
        assertEquals("task7/agent-error-behavior/null-result-message",
                "Error: Agent returned null result",
                failed.messages().get(failed.messages().size() - 1).text());
    }

    @Test
    void task7_agentErrorBehavior_loopErrorUsesErrorPayloadAndClearsLocale() {
        UUID sessionId = newSession();
        setSessionLocale(sessionId, "zh_cn");

        invokeHandleAgentLoopResult(AgentLoopResult.withError("loop exploded", 2), sessionId, null, "zh_cn");

        SessionSnapshot failed = requireSnapshot("task7/agent-error-behavior/loop-error", sessionId);
        assertEquals("task7/agent-error-behavior/loop-error-state", SessionState.FAILED, failed.state());
        assertEquals("task7/agent-error-behavior/loop-error-last-error",
                Optional.of("loop exploded"), failed.lastError());
        assertEquals("task7/agent-error-behavior/loop-error-message",
                "Error: loop exploded",
                failed.messages().get(failed.messages().size() - 1).text());
        assertTrue("task7/agent-error-behavior/loop-error-locale-cleared",
                !hasSessionLocale(sessionId));
    }

    @Test
    void task7_agentErrorBehavior_applyAgentErrorStoresPrefixAndState() {
        UUID sessionId = newSession();

        invokeApplyAgentError(sessionId, "Agent error: transport timeout");

        SessionSnapshot failed = requireSnapshot("task7/agent-error-behavior/apply-agent-error", sessionId);
        assertEquals("task7/agent-error-behavior/apply-agent-error-state", SessionState.FAILED, failed.state());
        assertEquals("task7/agent-error-behavior/apply-agent-error-last-error",
                Optional.of("Agent error: transport timeout"), failed.lastError());
        assertEquals("task7/agent-error-behavior/apply-agent-error-prefixed-message",
                "Error: Agent error: transport timeout",
                failed.messages().get(failed.messages().size() - 1).text());
    }

    @Test
    void task7_agentErrorBehavior_emptySuccessfulResultFallsBackToIdleAndClearsLocale() {
        UUID sessionId = newSession();
        setSessionLocale(sessionId, "fr_fr");
        assertTrue("task7/agent-error-behavior/empty-result-start-thinking",
                ChatMCNetwork.SESSIONS.tryStartThinking(sessionId));

        invokeHandleAgentLoopResult(new AgentLoopResult(true, Optional.empty(), Optional.empty(), Optional.empty(), 3),
                sessionId, null, "fr_fr");

        SessionSnapshot idle = requireSnapshot("task7/agent-error-behavior/empty-result", sessionId);
        assertEquals("task7/agent-error-behavior/empty-result-state", SessionState.IDLE, idle.state());
        assertTrue("task7/agent-error-behavior/empty-result-locale-cleared", !hasSessionLocale(sessionId));
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
            throw new AssertionError("task7/agent-error-behavior/invoke-handle-agent-loop-result", rootCause(exception));
        }
    }

    private static void invokeApplyAgentError(UUID sessionId, String message) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("applyAgentError", UUID.class, String.class);
            method.setAccessible(true);
            method.invoke(null, sessionId, message);
        } catch (Exception exception) {
            throw new AssertionError("task7/agent-error-behavior/invoke-apply-agent-error", rootCause(exception));
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
            throw new AssertionError("task7/agent-error-behavior/clear-session-locale", exception);
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
            throw new AssertionError("task7/agent-error-behavior/set-session-locale", exception);
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
            throw new AssertionError("task7/agent-error-behavior/has-session-locale", exception);
        }
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
            throw new AssertionError("task7/agent-error-behavior/bootstrap", exception);
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
