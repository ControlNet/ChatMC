package space.controlnet.mineagent.ae.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.mineagent.ae.common.MineAgentAe;

@Mod(MineAgentAe.MOD_ID)
public final class MineAgentAeForge {
    public MineAgentAeForge() {
        EventBuses.registerModEventBus(MineAgentAe.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        MineAgentAe.init();
    }
}
