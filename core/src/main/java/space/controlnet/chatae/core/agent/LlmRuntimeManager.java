package space.controlnet.chatae.core.agent;

/**
 * Manages the LLM runtime lifecycle.
 * This class coordinates config loading and runtime updates.
 */
public final class LlmRuntimeManager {
    private LlmRuntimeManager() {
    }

    /**
     * Callback interface for runtime events.
     */
    public interface RuntimeEventHandler {
        /**
         * Called when the LLM cooldown should be updated.
         */
        void onCooldownUpdated(long cooldownMillis);

        /**
         * Called when the LLM timeout should be updated.
         */
        void onTimeoutUpdated(long timeoutMillis);

        void onReloadFailed(String message);
    }

    /**
     * Reloads the LLM runtime with the given config.
     *
     * @param config  the LLM configuration
     * @param handler optional event handler for runtime updates
     */
    public static boolean reload(LlmConfig config, RuntimeEventHandler handler) {
        boolean reloaded = LlmRuntime.reload(config);
        if (handler != null) {
            if (reloaded) {
                handler.onCooldownUpdated(config.cooldownMillis());
                handler.onTimeoutUpdated(config.timeout().toMillis());
            } else {
                handler.onReloadFailed("Failed to initialize LLM model. Check provider configuration.");
            }
        }
        return reloaded;
    }

    /**
     * Clears the LLM runtime.
     */
    public static void clear() {
        LlmRuntime.clear();
    }
}
