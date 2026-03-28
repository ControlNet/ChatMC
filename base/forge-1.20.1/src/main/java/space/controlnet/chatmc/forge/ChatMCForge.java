package space.controlnet.chatmc.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCClient;

@Mod(ChatMC.MOD_ID)
public final class ChatMCForge {
    public ChatMCForge() {
        EventBuses.registerModEventBus(ChatMC.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ChatMC.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ChatMCForge::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ChatMCClient::init);
    }
}
