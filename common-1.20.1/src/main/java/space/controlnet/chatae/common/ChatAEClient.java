package space.controlnet.chatae.common;

import space.controlnet.chatae.common.client.ChatAEScreens;

public final class ChatAEClient {
    private ChatAEClient() {
    }

    public static void init() {
        ChatAEScreens.init();
    }
}
