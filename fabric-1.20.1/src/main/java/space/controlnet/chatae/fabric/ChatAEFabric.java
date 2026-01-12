package space.controlnet.chatae.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.chatae.common.ChatAE;

public final class ChatAEFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatAE.init();
    }
}
