package space.controlnet.mineagent.common.client;

import dev.architectury.registry.menu.MenuRegistry;
import space.controlnet.mineagent.common.MineAgentRegistries;
import space.controlnet.mineagent.common.client.screen.AiTerminalScreen;

public final class MineAgentScreens {
    private MineAgentScreens() {
    }

    public static void init() {
        MenuRegistry.registerScreenFactory(MineAgentRegistries.AI_TERMINAL_MENU.get(), AiTerminalScreen::new);
    }
}
