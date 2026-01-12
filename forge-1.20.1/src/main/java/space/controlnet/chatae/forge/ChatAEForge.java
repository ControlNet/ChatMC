package space.controlnet.chatae.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.ChatAEClient;

@Mod(ChatAE.MOD_ID)
public final class ChatAEForge {
    public ChatAEForge() {
        EventBuses.registerModEventBus(ChatAE.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ChatAE.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ChatAEForge::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ChatAEClient::init);
    }
}
