package space.controlnet.chatmc.fabric;

import net.fabricmc.api.ClientModInitializer;
import space.controlnet.chatmc.common.ChatMCClient;

public final class ChatMCFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatMCClient.init();
    }
}
