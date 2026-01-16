package space.controlnet.chatae.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAENetwork;
import space.controlnet.chatae.core.agent.LlmConfig;
import space.controlnet.chatae.core.agent.LlmConfigValidator;
import space.controlnet.chatae.core.agent.LlmRuntimeManager;

/**
 * MC-specific wrapper for LLM runtime management.
 * Handles config loading from files and coordinates with ChatAENetwork.
 */
public final class McRuntimeManager {
    private McRuntimeManager() {
    }

    public static void reload(MinecraftServer server) {
        LlmConfig config = LlmConfigLoader.load(server);
        var errors = LlmConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            space.controlnet.chatae.common.ChatAE.LOGGER.warn("LLM config validation failed: {}", String.join(" ", errors));
            return;
        }
        LlmRuntimeManager.reload(config, new LlmRuntimeManager.RuntimeEventHandler() {
            @Override
            public void onCooldownUpdated(long cooldownMillis) {
                ChatAENetwork.updateLlmCooldown(cooldownMillis);
            }

            @Override
            public void onTimeoutUpdated(long timeoutMillis) {
                ChatAENetwork.updateLlmTimeout(timeoutMillis);
            }

            @Override
            public void onMaxToolCallsUpdated(int maxToolCalls) {
                ChatAENetwork.updateAgentMaxToolCalls(maxToolCalls);
            }

            @Override
            public void onMaxIterationsUpdated(int maxIterations) {
                ChatAENetwork.updateAgentMaxIterations(maxIterations);
            }

            @Override
            public void onMaxHistoryMessagesUpdated(int maxHistoryMessages) {
                ChatAENetwork.updateAgentMaxHistoryMessages(maxHistoryMessages);
            }

            @Override
            public void onLogResponsesUpdated(boolean logResponses) {
                ChatAENetwork.updateAgentLogResponses(logResponses);
            }

            @Override
            public void onReloadFailed(String message) {
                space.controlnet.chatae.common.ChatAE.LOGGER.warn(message);
            }
        });
    }

    public static void clear() {
        LlmRuntimeManager.clear();
    }
}
