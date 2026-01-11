package space.controlnet.chatae;

import space.controlnet.chatae.client.ChatAEScreens;

public final class ChatAEClient {
    private ChatAEClient() {
    }

    public static void init() {
        ChatAEScreens.init();
    }
}
