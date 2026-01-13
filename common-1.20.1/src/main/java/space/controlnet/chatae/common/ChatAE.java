package space.controlnet.chatae.common;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.controlnet.chatae.common.part.ChatAEPartRegistries;
import space.controlnet.chatae.common.recipes.RecipeIndexReloadListener;
import space.controlnet.chatae.common.recipes.RecipeIndexService;

import java.util.concurrent.atomic.AtomicReference;

public final class ChatAE {
    public static final String MOD_ID = "chatae";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final RecipeIndexService RECIPE_INDEX = new RecipeIndexService();

    private static final AtomicReference<MinecraftServer> SERVER = new AtomicReference<>();

    private ChatAE() {
    }

    public static void init() {
        ChatAERegistries.init();
        ChatAEPartRegistries.init();
        ChatAENetwork.init();
        space.controlnet.chatae.common.commands.ChatAECommands.init();

        ReloadListenerRegistry.register(PackType.SERVER_DATA, new RecipeIndexReloadListener(RECIPE_INDEX, SERVER::get), ChatAERegistries.id("recipe_index"));

        LifecycleEvent.SERVER_STARTED.register(server -> {
            SERVER.set(server);
            ChatAENetwork.setServer(server);
            space.controlnet.chatae.common.llm.PromptRuntime.reload(server);
            space.controlnet.chatae.common.llm.LlmRuntimeManager.reload(server);
            RECIPE_INDEX.rebuildAsync(server);
        });

        LifecycleEvent.SERVER_STOPPED.register(server -> {
            ChatAENetwork.setServer(null);
            SERVER.set(null);
            space.controlnet.chatae.common.llm.LlmRuntimeManager.clear();
            RECIPE_INDEX.shutdown();
            ChatAENetwork.shutdown();
        });

        LOGGER.info("ChatAE initialized");
    }
}
