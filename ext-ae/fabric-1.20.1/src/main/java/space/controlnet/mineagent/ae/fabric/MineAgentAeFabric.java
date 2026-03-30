package space.controlnet.mineagent.ae.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.mineagent.ae.common.MineAgentAe;

public final class MineAgentAeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MineAgentAe.init();
    }
}
