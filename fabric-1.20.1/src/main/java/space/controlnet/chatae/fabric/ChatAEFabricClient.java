package space.controlnet.chatae.fabric;

import net.fabricmc.api.ClientModInitializer;
import space.controlnet.chatae.ChatAEClient;

public final class ChatAEFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatAEClient.init();
    }
}
