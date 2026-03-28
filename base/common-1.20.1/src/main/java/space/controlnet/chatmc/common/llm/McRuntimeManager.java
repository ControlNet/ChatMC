package space.controlnet.chatmc.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.core.agent.LlmConfig;
import space.controlnet.chatmc.core.agent.LlmConfigValidator;
import space.controlnet.chatmc.core.agent.LlmRuntimeManager;

/**
 * MC-specific wrapper for LLM runtime management.
 * Handles config loading from files and coordinates with ChatMCNetwork.
 */
public final class McRuntimeManager {
    private McRuntimeManager() {
    }

    public static void reload(MinecraftServer server) {
        LlmConfig config = LlmConfigLoader.load(server);
        var errors = LlmConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            space.controlnet.chatmc.common.ChatMC.LOGGER.warn("LLM config validation failed: {}", String.join(" ", errors));
            return;
        }
        LlmRuntimeManager.reload(config, new LlmRuntimeManager.RuntimeEventHandler() {
            @Override
            public void onCooldownUpdated(long cooldownMillis) {
                ChatMCNetwork.updateLlmCooldown(cooldownMillis);
            }

            @Override
            public void onTimeoutUpdated(long timeoutMillis) {
                ChatMCNetwork.updateLlmTimeout(timeoutMillis);
            }

            @Override
            public void onMaxToolCallsUpdated(int maxToolCalls) {
                ChatMCNetwork.updateAgentMaxToolCalls(maxToolCalls);
            }

            @Override
            public void onMaxIterationsUpdated(int maxIterations) {
                ChatMCNetwork.updateAgentMaxIterations(maxIterations);
            }

            @Override
            public void onLogResponsesUpdated(boolean logResponses) {
                ChatMCNetwork.updateAgentLogResponses(logResponses);
            }

            @Override
            public void onMaxRetriesUpdated(int maxRetries) {
                ChatMCNetwork.updateAgentMaxRetries(maxRetries);
            }

            @Override
            public void onReloadFailed(String message) {
                space.controlnet.chatmc.common.ChatMC.LOGGER.warn(message);
            }
        });
    }

    public static void clear() {
        LlmRuntimeManager.clear();
    }
}
