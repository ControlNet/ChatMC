package space.controlnet.chatae.core.audit;

import space.controlnet.chatae.core.policy.RiskLevel;

public record AuditEvent(
        String playerId,
        long timestampMillis,
        String toolName,
        String sanitizedArgsJson,
        RiskLevel riskLevel,
        String decision,
        long durationMillis,
        AuditOutcome outcome,
        String error
) {
}
