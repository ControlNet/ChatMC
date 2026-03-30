package space.controlnet.mineagent.matrix.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.mineagent.matrix.common.MineAgentMatrix;

@Mod(MineAgentMatrix.MOD_ID)
public final class MineAgentMatrixForge {
    public MineAgentMatrixForge() {
        EventBuses.registerModEventBus(MineAgentMatrix.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        MineAgentMatrix.init();
    }
}
