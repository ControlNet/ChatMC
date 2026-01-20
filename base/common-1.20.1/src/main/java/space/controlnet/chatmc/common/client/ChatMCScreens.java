package space.controlnet.chatmc.common.client;

import dev.architectury.registry.menu.MenuRegistry;
import space.controlnet.chatmc.common.ChatMCRegistries;
import space.controlnet.chatmc.common.client.screen.AiTerminalScreen;

public final class ChatMCScreens {
    private ChatMCScreens() {
    }

    public static void init() {
        MenuRegistry.registerScreenFactory(ChatMCRegistries.AI_TERMINAL_MENU.get(), AiTerminalScreen::new);
    }
}
