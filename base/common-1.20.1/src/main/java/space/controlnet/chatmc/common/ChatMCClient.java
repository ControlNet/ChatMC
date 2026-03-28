package space.controlnet.chatmc.common;

import space.controlnet.chatmc.common.client.ChatMCScreens;
import space.controlnet.chatmc.common.client.ChatMCKeybinds;

public final class ChatMCClient {
    private ChatMCClient() {
    }

    public static void init() {
        ChatMCScreens.init();
        ChatMCKeybinds.init();
    }
}
