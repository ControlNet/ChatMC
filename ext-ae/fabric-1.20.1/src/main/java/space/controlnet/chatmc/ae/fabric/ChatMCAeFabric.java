package space.controlnet.chatmc.ae.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.chatmc.ae.common.ChatMCAe;

public final class ChatMCAeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatMCAe.init();
    }
}
