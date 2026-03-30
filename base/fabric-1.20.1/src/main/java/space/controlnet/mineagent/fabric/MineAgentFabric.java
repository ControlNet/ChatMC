package space.controlnet.mineagent.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.mineagent.common.MineAgent;

public final class MineAgentFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MineAgent.init();
    }
}
