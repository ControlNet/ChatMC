package space.controlnet.mineagent.ae.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.controlnet.mineagent.ae.common.client.AeToolOutputRenderer;
import space.controlnet.mineagent.ae.common.part.MineAgentAePartRegistries;
import space.controlnet.mineagent.ae.common.terminal.AeTerminalContextResolver;
import space.controlnet.mineagent.ae.common.tools.AeToolProvider;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.common.terminal.TerminalContextRegistry;
import space.controlnet.mineagent.common.tools.ToolRegistry;

public final class MineAgentAe {
    public static final String MOD_ID = "mineagentae";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private MineAgentAe() {
    }

    public static void init() {
        MineAgentAePartRegistries.init();
        ToolRegistry.register("ae", new AeToolProvider());
        TerminalContextRegistry.register(new AeTerminalContextResolver());
        ToolOutputRendererRegistry.register(new AeToolOutputRenderer());
        LOGGER.info("MineAgentAe initialized");
    }
}
