package space.controlnet.chatae.core.audit;

/**
 * Platform-neutral audit logging interface.
 * Implementations should provide logging functionality for audit events.
 */
public interface AuditLogger {
    /**
     * Logs an audit event.
     * @param event The audit event to log
     */
    void log(AuditEvent event);
}
