package space.controlnet.chatae.common.client;

import dev.architectury.registry.menu.MenuRegistry;
import space.controlnet.chatae.common.ChatAERegistries;
import space.controlnet.chatae.common.client.screen.AiTerminalScreen;

public final class ChatAEScreens {
    private ChatAEScreens() {
    }

    public static void init() {
        MenuRegistry.registerScreenFactory(ChatAERegistries.AI_TERMINAL_MENU.get(), AiTerminalScreen::new);
    }
}
