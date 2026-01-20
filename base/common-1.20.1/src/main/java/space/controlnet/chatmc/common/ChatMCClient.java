package space.controlnet.chatmc.common;

import space.controlnet.chatmc.common.client.ChatMCScreens;

public final class ChatMCClient {
    private ChatMCClient() {
    }

    public static void init() {
        ChatMCScreens.init();
    }
}
