package space.controlnet.mineagent.common.audit;

import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.core.audit.AuditEvent;
import space.controlnet.mineagent.core.audit.LlmAuditEvent;

/**
 * Minecraft-specific implementation of AuditLogger.
 */
public final class AuditLogger implements space.controlnet.mineagent.core.audit.AuditLogger {
    private AuditLogger() {
    }

    private static final AuditLogger INSTANCE = new AuditLogger();

    public static AuditLogger instance() {
        return INSTANCE;
    }

    @Override
    public void log(AuditEvent event) {
        MineAgent.LOGGER.info("audit player={} tool={} risk={} decision={} outcome={} duration={}ms error={}",
                event.playerId(), event.toolName(), event.riskLevel(), event.decision(), event.outcome(), event.durationMillis(), event.error());
    }

    @Override
    public void logLlm(LlmAuditEvent event) {
        MineAgent.LOGGER.info("llm_audit player={} prompt={} locale={} iteration={} outcome={} duration={}ms error={}",
                event.playerId(), event.promptId(), event.locale(), event.iteration(), event.outcome(), event.durationMillis(), event.error());
    }
}
