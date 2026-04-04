package space.controlnet.mineagent.common;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.controlnet.mineagent.common.client.render.HttpToolOutputRenderer;
import space.controlnet.mineagent.common.client.render.McToolOutputRenderer;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.common.recipes.RecipeIndexReloadListener;
import space.controlnet.mineagent.common.recipes.RecipeIndexService;
import space.controlnet.mineagent.common.tools.McToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.common.tools.http.HttpToolProvider;
import space.controlnet.mineagent.common.tools.mcp.McpRuntimeManager;

import java.util.concurrent.atomic.AtomicReference;

public final class MineAgent {
    public static final String MOD_ID = "mineagent";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final RecipeIndexService RECIPE_INDEX = new RecipeIndexService();

    private static final AtomicReference<MinecraftServer> SERVER = new AtomicReference<>();

    private MineAgent() {
    }

    public static void init() {
        MineAgentRegistries.init();
        ToolRegistry.register("mc", new McToolProvider());
        ToolRegistry.setGroupId("mc", MOD_ID);
        ToolRegistry.register("http", new HttpToolProvider());
        ToolRegistry.setGroupId("http", MOD_ID);
        ToolOutputRendererRegistry.register(new McToolOutputRenderer());
        ToolOutputRendererRegistry.register(new HttpToolOutputRenderer());
        MineAgentNetwork.init();
        space.controlnet.mineagent.common.commands.MineAgentCommands.init();

        ReloadListenerRegistry.register(PackType.SERVER_DATA, new RecipeIndexReloadListener(RECIPE_INDEX, SERVER::get), MineAgentRegistries.id("recipe_index"));

        LifecycleEvent.SERVER_STARTED.register(server -> {
            SERVER.set(server);
            MineAgentNetwork.setServer(server);
            space.controlnet.mineagent.common.llm.PromptRuntime.reload(server);
            space.controlnet.mineagent.common.llm.McRuntimeManager.reload(server);
            McpRuntimeManager.reload(server);
            RECIPE_INDEX.rebuildAsync(server);
        });

        LifecycleEvent.SERVER_STOPPED.register(server -> {
            MineAgentNetwork.setServer(null);
            SERVER.set(null);
            space.controlnet.mineagent.common.llm.McRuntimeManager.clear();
            McpRuntimeManager.clear();
            RECIPE_INDEX.shutdown();
            MineAgentNetwork.shutdown();
        });

        LOGGER.info("MineAgent initialized");
    }
}
