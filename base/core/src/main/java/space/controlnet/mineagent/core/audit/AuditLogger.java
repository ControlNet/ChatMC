package space.controlnet.mineagent.core.audit;

/**
 * Platform-neutral audit logging interface.
 * Implementations should provide logging functionality for audit events.
 */
public interface AuditLogger {
    /**
     * Logs a tool execution audit event.
     * @param event The audit event to log
     */
    void log(AuditEvent event);

    /**
     * Logs an LLM call audit event.
     * @param event The LLM audit event to log
     */
    void logLlm(LlmAuditEvent event);
}
