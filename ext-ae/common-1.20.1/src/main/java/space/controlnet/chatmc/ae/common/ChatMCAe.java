package space.controlnet.chatmc.ae.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.controlnet.chatmc.ae.common.client.AeToolOutputRenderer;
import space.controlnet.chatmc.ae.common.part.ChatMCAePartRegistries;
import space.controlnet.chatmc.ae.common.terminal.AeTerminalContextResolver;
import space.controlnet.chatmc.ae.common.tools.AeToolProvider;
import space.controlnet.chatmc.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.chatmc.common.terminal.TerminalContextRegistry;
import space.controlnet.chatmc.common.tools.ToolRegistry;

public final class ChatMCAe {
    public static final String MOD_ID = "chatmcae";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private ChatMCAe() {
    }

    public static void init() {
        ChatMCAePartRegistries.init();
        ToolRegistry.register("ae", new AeToolProvider());
        TerminalContextRegistry.register(new AeTerminalContextResolver());
        ToolOutputRendererRegistry.register(new AeToolOutputRenderer());
        LOGGER.info("ChatMCAe initialized");
    }
}
