package space.controlnet.mineagent.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentClient;

@Mod(MineAgent.MOD_ID)
public final class MineAgentForge {
    public MineAgentForge() {
        EventBuses.registerModEventBus(MineAgent.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        MineAgent.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(MineAgentForge::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(MineAgentClient::init);
    }
}
