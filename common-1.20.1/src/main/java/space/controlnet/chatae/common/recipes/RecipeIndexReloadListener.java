package space.controlnet.chatae.common.recipes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class RecipeIndexReloadListener implements PreparableReloadListener {
    private final RecipeIndexService recipeIndexService;
    private final Supplier<MinecraftServer> serverSupplier;

    public RecipeIndexReloadListener(RecipeIndexService recipeIndexService, Supplier<MinecraftServer> serverSupplier) {
        this.recipeIndexService = recipeIndexService;
        this.serverSupplier = serverSupplier;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier,
                                         ResourceManager resourceManager,
                                         ProfilerFiller preparationsProfiler,
                                         ProfilerFiller reloadProfiler,
                                         Executor backgroundExecutor,
                                         Executor gameExecutor) {
        MinecraftServer server = serverSupplier.get();
        if (server != null) {
            recipeIndexService.rebuildAsync(server);
        }
        return barrier.wait(null);
    }
}
