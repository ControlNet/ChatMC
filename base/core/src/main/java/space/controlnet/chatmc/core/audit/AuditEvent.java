package space.controlnet.chatmc.core.audit;

import space.controlnet.chatmc.core.policy.RiskLevel;

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
