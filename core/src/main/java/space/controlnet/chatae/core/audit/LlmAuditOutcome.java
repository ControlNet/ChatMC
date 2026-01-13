package space.controlnet.chatae.core.audit;

/**
 * Outcome of an LLM call for audit purposes.
 */
public enum LlmAuditOutcome {
    SUCCESS,
    TIMEOUT,
    RATE_LIMITED,
    ERROR,
    PARSE_ERROR
}
