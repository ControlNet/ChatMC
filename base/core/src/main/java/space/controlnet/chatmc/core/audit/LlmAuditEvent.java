package space.controlnet.chatmc.core.audit;

/**
 * Audit event for LLM calls in the agent loop.
 */
public record LlmAuditEvent(
        String playerId,
        long timestampMillis,
        String promptId,
        String locale,
        int iteration,
        long durationMillis,
        LlmAuditOutcome outcome,
        String error
) {
}
