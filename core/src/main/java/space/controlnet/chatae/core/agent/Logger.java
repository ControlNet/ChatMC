package space.controlnet.chatae.core.agent;

/**
 * Simple logging interface for agent components.
 * Platform implementations should provide appropriate logging.
 */
@FunctionalInterface
public interface Logger {
    /**
     * Logs a warning message with an optional exception.
     * @param message The warning message
     * @param exception Optional exception (may be null)
     */
    void warn(String message, Throwable exception);
}
