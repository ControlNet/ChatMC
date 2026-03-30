package space.controlnet.mineagent.common;

import space.controlnet.mineagent.common.client.MineAgentScreens;
import space.controlnet.mineagent.common.client.MineAgentKeybinds;

public final class MineAgentClient {
    private MineAgentClient() {
    }

    public static void init() {
        MineAgentScreens.init();
        MineAgentKeybinds.init();
    }
}
