package space.controlnet.mineagent.fabric;

import net.fabricmc.api.ClientModInitializer;
import space.controlnet.mineagent.common.MineAgentClient;

public final class MineAgentFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MineAgentClient.init();
    }
}
