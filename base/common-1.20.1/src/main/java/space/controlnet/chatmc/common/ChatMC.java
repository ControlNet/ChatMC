package space.controlnet.chatmc.common;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.controlnet.chatmc.common.client.render.McToolOutputRenderer;
import space.controlnet.chatmc.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.chatmc.common.recipes.RecipeIndexReloadListener;
import space.controlnet.chatmc.common.recipes.RecipeIndexService;
import space.controlnet.chatmc.common.tools.McToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;

import java.util.concurrent.atomic.AtomicReference;

public final class ChatMC {
    public static final String MOD_ID = "chatmc";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final RecipeIndexService RECIPE_INDEX = new RecipeIndexService();

    private static final AtomicReference<MinecraftServer> SERVER = new AtomicReference<>();

    private ChatMC() {
    }

    public static void init() {
        ChatMCRegistries.init();
        ToolRegistry.register("mc", new McToolProvider());
        ToolOutputRendererRegistry.register(new McToolOutputRenderer());
        ChatMCNetwork.init();
        space.controlnet.chatmc.common.commands.ChatMCCommands.init();

        ReloadListenerRegistry.register(PackType.SERVER_DATA, new RecipeIndexReloadListener(RECIPE_INDEX, SERVER::get), ChatMCRegistries.id("recipe_index"));

        LifecycleEvent.SERVER_STARTED.register(server -> {
            SERVER.set(server);
            ChatMCNetwork.setServer(server);
            space.controlnet.chatmc.common.llm.PromptRuntime.reload(server);
            space.controlnet.chatmc.common.llm.McRuntimeManager.reload(server);
            RECIPE_INDEX.rebuildAsync(server);
        });

        LifecycleEvent.SERVER_STOPPED.register(server -> {
            ChatMCNetwork.setServer(null);
            SERVER.set(null);
            space.controlnet.chatmc.common.llm.McRuntimeManager.clear();
            RECIPE_INDEX.shutdown();
            ChatMCNetwork.shutdown();
        });

        LOGGER.info("ChatMC initialized");
    }
}
