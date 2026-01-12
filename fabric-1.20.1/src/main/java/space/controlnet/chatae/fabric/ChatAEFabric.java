package space.controlnet.chatae.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.fabric.FabricPartRegistries;

public final class ChatAEFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatAE.init();
        FabricPartRegistries.init();
    }
}
