package space.controlnet.mineagent.matrix.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.mineagent.matrix.common.MineAgentMatrix;

public final class MineAgentMatrixFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MineAgentMatrix.init();
    }
}
