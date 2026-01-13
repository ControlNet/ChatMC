package space.controlnet.chatae.common.llm;

import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.core.agent.LlmConfig;
import space.controlnet.chatae.core.agent.LlmRuntime;

public final class LlmRuntimeManager {
    private LlmRuntimeManager() {
    }

    public static void reload(MinecraftServer server) {
        LlmConfig config = LlmConfigLoader.load(server);
        LlmRuntime.reload(config);
        space.controlnet.chatae.common.ChatAENetwork.updateLlmCooldown(config.cooldownMillis());
    }

    public static void clear() {
        LlmRuntime.clear();
    }
}
