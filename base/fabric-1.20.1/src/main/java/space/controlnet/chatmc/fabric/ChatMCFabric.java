package space.controlnet.chatmc.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.chatmc.common.ChatMC;

public final class ChatMCFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatMC.init();
    }
}
