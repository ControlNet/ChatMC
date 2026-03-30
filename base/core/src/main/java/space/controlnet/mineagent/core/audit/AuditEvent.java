package space.controlnet.mineagent.core.audit;

import space.controlnet.mineagent.core.policy.RiskLevel;

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
