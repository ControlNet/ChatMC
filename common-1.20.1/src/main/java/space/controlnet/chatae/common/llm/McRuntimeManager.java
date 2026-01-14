package space.controlnet.chatae.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAENetwork;
import space.controlnet.chatae.core.agent.LlmConfig;
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
        LlmRuntimeManager.reload(config, new LlmRuntimeManager.RuntimeEventHandler() {
            @Override
            public void onCooldownUpdated(long cooldownMillis) {
                ChatAENetwork.updateLlmCooldown(cooldownMillis);
            }

            @Override
            public void onTimeoutUpdated(long timeoutMillis) {
                ChatAENetwork.updateLlmTimeout(timeoutMillis);
            }
        });
    }

    public static void clear() {
        LlmRuntimeManager.clear();
    }
}
