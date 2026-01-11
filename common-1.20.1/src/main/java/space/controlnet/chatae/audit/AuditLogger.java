package space.controlnet.chatae.audit;

import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.core.audit.AuditEvent;

public final class AuditLogger {
    private AuditLogger() {
    }

    public static void log(AuditEvent event) {
        ChatAE.LOGGER.info("audit player={} tool={} risk={} decision={} outcome={} duration={}ms error={}",
                event.playerId(), event.toolName(), event.riskLevel(), event.decision(), event.outcome(), event.durationMillis(), event.error());
    }
}
