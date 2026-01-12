package space.controlnet.chatae.common.audit;

import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.audit.AuditEvent;

/**
 * Minecraft-specific implementation of AuditLogger.
 */
public final class AuditLogger implements space.controlnet.chatae.core.audit.AuditLogger {
    private AuditLogger() {
    }

    private static final AuditLogger INSTANCE = new AuditLogger();

    public static AuditLogger instance() {
        return INSTANCE;
    }

    @Override
    public void log(AuditEvent event) {
        ChatAE.LOGGER.info("audit player={} tool={} risk={} decision={} outcome={} duration={}ms error={}",
                event.playerId(), event.toolName(), event.riskLevel(), event.decision(), event.outcome(), event.durationMillis(), event.error());
    }
}
