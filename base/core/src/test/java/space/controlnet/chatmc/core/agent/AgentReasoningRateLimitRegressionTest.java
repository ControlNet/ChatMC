package space.controlnet.chatmc.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.chatmc.core.audit.AuditEvent;
import space.controlnet.chatmc.core.audit.AuditLogger;
import space.controlnet.chatmc.core.audit.LlmAuditEvent;
import space.controlnet.chatmc.core.audit.LlmAuditOutcome;

import java.util.ArrayList;
import java.util.UUID;

public final class AgentReasoningRateLimitRegressionTest {
    @Test
    void task18_rateLimitReasoning_repeatedNewRequestRemainsBlockedByCooldown() {
        LlmRateLimiter limiter = new LlmRateLimiter(60_000L);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000184");
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        LlmRuntime.clear();

        AgentReasoningService reasoningService = new AgentReasoningService(
                (message, throwable) -> {
                },
                limiter,
                null,
                0,
                auditLogger
        );

        assertTrue("task18/rate-limit/reasoning/first-empty", reasoningService.reason(playerId, "first prompt", "en_us", 0).isEmpty());
        assertTrue("task18/rate-limit/reasoning/second-empty", reasoningService.reason(playerId, "second prompt", "en_us", 0).isEmpty());
        assertEquals("task18/rate-limit/reasoning/audit-count", 2, auditLogger.llmEvents.size());
        assertEquals("task18/rate-limit/reasoning/first-outcome", LlmAuditOutcome.ERROR, auditLogger.llmEvents.get(0).outcome());
        assertEquals("task18/rate-limit/reasoning/second-outcome", LlmAuditOutcome.RATE_LIMITED, auditLogger.llmEvents.get(1).outcome());
    }

    @Test
    void task18_rateLimitReasoning_followupIterationBypassesLimiterAfterFirstCall() {
        LlmRateLimiter limiter = new LlmRateLimiter(60_000L);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000185");
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        LlmRuntime.clear();

        AgentReasoningService reasoningService = new AgentReasoningService(
                (message, throwable) -> {
                },
                limiter,
                null,
                0,
                auditLogger
        );

        assertTrue("task18/rate-limit/reasoning/iteration-zero-empty", reasoningService.reason(playerId, "iteration zero", "en_us", 0).isEmpty());
        assertTrue("task18/rate-limit/reasoning/followup-empty", reasoningService.reason(playerId, "iteration one", "en_us", 1).isEmpty());
        assertEquals("task18/rate-limit/reasoning/followup-audit-count", 2, auditLogger.llmEvents.size());
        assertEquals("task18/rate-limit/reasoning/iteration-zero-outcome", LlmAuditOutcome.ERROR, auditLogger.llmEvents.get(0).outcome());
        assertEquals("task18/rate-limit/reasoning/followup-outcome", LlmAuditOutcome.ERROR, auditLogger.llmEvents.get(1).outcome());
        assertEquals("task18/rate-limit/reasoning/followup-iteration", 1, auditLogger.llmEvents.get(1).iteration());
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean condition) {
        if (!condition) {
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

    private static final class RecordingAuditLogger implements AuditLogger {
        private final java.util.List<LlmAuditEvent> llmEvents = new ArrayList<>();

        @Override
        public void log(AuditEvent event) {
        }

        @Override
        public void logLlm(LlmAuditEvent event) {
            llmEvents.add(event);
        }
    }
}
