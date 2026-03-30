package space.controlnet.mineagent.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.core.agent.LlmConfig;
import space.controlnet.mineagent.core.agent.LlmConfigValidator;
import space.controlnet.mineagent.core.agent.LlmRuntimeManager;

/**
 * MC-specific wrapper for LLM runtime management.
 * Handles config loading from files and coordinates with MineAgentNetwork.
 */
public final class McRuntimeManager {
    private McRuntimeManager() {
    }

    public static void reload(MinecraftServer server) {
        LlmConfig config = LlmConfigLoader.load(server);
        var errors = LlmConfigValidator.validate(config);
        if (!errors.isEmpty()) {
            space.controlnet.mineagent.common.MineAgent.LOGGER.warn("LLM config validation failed: {}", String.join(" ", errors));
            return;
        }
        LlmRuntimeManager.reload(config, new LlmRuntimeManager.RuntimeEventHandler() {
            @Override
            public void onCooldownUpdated(long cooldownMillis) {
                MineAgentNetwork.updateLlmCooldown(cooldownMillis);
            }

            @Override
            public void onTimeoutUpdated(long timeoutMillis) {
                MineAgentNetwork.updateLlmTimeout(timeoutMillis);
            }

            @Override
            public void onMaxToolCallsUpdated(int maxToolCalls) {
                MineAgentNetwork.updateAgentMaxToolCalls(maxToolCalls);
            }

            @Override
            public void onMaxIterationsUpdated(int maxIterations) {
                MineAgentNetwork.updateAgentMaxIterations(maxIterations);
            }

            @Override
            public void onLogResponsesUpdated(boolean logResponses) {
                MineAgentNetwork.updateAgentLogResponses(logResponses);
            }

            @Override
            public void onMaxRetriesUpdated(int maxRetries) {
                MineAgentNetwork.updateAgentMaxRetries(maxRetries);
            }

            @Override
            public void onReloadFailed(String message) {
                space.controlnet.mineagent.common.MineAgent.LOGGER.warn(message);
            }
        });
    }

    public static void clear() {
        LlmRuntimeManager.clear();
    }
}
