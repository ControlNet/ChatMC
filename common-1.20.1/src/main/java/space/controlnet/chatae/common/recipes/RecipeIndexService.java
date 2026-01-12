package space.controlnet.chatae.common.recipes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import space.controlnet.chatae.core.recipes.RecipeSearchFilters;
import space.controlnet.chatae.core.recipes.RecipeSearchResult;
import space.controlnet.chatae.core.recipes.RecipeSearchAlgorithm;
import space.controlnet.chatae.core.recipes.RecipeSummary;
import space.controlnet.chatae.core.recipes.RecipeIndexSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class RecipeIndexService {
    private final AtomicReference<ExecutorService> indexExecutorRef = new AtomicReference<>();

    private final AtomicReference<RecipeIndexSnapshot> snapshotRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> indexingRef = new AtomicReference<>();

    public boolean isReady() {
        return snapshotRef.get() != null;
    }

    public Optional<CompletableFuture<Void>> indexingFuture() {
        return Optional.ofNullable(indexingRef.get());
    }

    public void shutdown() {
        ExecutorService executor = indexExecutorRef.getAndSet(null);
        if (executor != null) {
            executor.shutdownNow();
        }
        indexingRef.set(null);
    }

    public CompletableFuture<Void> rebuildAsync(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        List<ResourceLocation> recipeIds = recipeManager.getRecipeIds().toList();

        snapshotRef.set(null);

        ExecutorService executor = getOrCreateExecutor();
        CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(() -> buildSnapshot(server, recipeManager, recipeIds), executor)
                .thenAccept(snapshotRef::set);

        indexingRef.set(future);
        return future;
    }

    private ExecutorService getOrCreateExecutor() {
        ExecutorService existing = indexExecutorRef.get();
        if (existing != null && !existing.isShutdown() && !existing.isTerminated()) {
            return existing;
        }

        ExecutorService created = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chatae-recipe-index");
            t.setDaemon(true);
            return t;
        });

        if (indexExecutorRef.compareAndSet(existing, created)) {
            if (existing != null) {
                existing.shutdownNow();
            }
            return created;
        }

        created.shutdownNow();
        return getOrCreateExecutor();
    }

    public RecipeSearchResult search(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        return RecipeSearchAlgorithm.search(snapshot, query, filters, pageToken, limit);
    }

    public Optional<RecipeSummary> get(String recipeId) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.byId().get(recipeId));
    }


    private static RecipeIndexSnapshot buildSnapshot(MinecraftServer server, RecipeManager recipeManager, List<ResourceLocation> recipeIds) {
        Map<String, RecipeSummary> byId = new HashMap<>();
        Map<String, List<String>> byOutput = new HashMap<>();
        Map<String, List<String>> byIngredient = new HashMap<>();
        Map<String, List<String>> byTag = new HashMap<>();
        Map<String, List<String>> byKeyword = new HashMap<>();

        for (ResourceLocation id : recipeIds) {
            Optional<? extends Recipe<?>> recipeOpt = recipeManager.byKey(id);
            if (recipeOpt.isEmpty()) {
                continue;
            }

            Recipe<?> recipe = recipeOpt.get();

            String recipeId = id.toString();
            String typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();

            ItemStack result = recipe.getResultItem(server.registryAccess());
            String outputItemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            int outputCount = result.getCount();

            List<String> ingredientItemIds = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack match : ingredient.getItems()) {
                    String ingId = BuiltInRegistries.ITEM.getKey(match.getItem()).toString();
                    ingredientItemIds.add(ingId);

                    addIndex(byIngredient, ingId, recipeId);

                    var resourceKey = BuiltInRegistries.ITEM.getResourceKey(match.getItem()).orElse(null);
                    Holder.Reference<Item> ref = resourceKey != null ? BuiltInRegistries.ITEM.getHolder(resourceKey).orElse(null) : null;
                    if (ref != null) {
                        ref.tags().map(TagKey::location).map(ResourceLocation::toString).forEach(tagId -> addIndex(byTag, tagId, recipeId));
                    }
                }
            }

            RecipeSummary summary = new RecipeSummary(recipeId, typeId, outputItemId, outputCount, ingredientItemIds);
            byId.put(recipeId, summary);

            addIndex(byOutput, outputItemId, recipeId);

            for (String token : RecipeSearchAlgorithm.tokenize(outputItemId)) {
                addIndex(byKeyword, token, recipeId);
            }

            String displayName = result.getHoverName().getString();
            for (String token : RecipeSearchAlgorithm.tokenize(displayName)) {
                addIndex(byKeyword, token, recipeId);
            }

            addIndex(byKeyword, id.getNamespace(), recipeId);
            addIndex(byKeyword, id.getPath(), recipeId);
        }

        freezeIndex(byOutput);
        freezeIndex(byIngredient);
        freezeIndex(byTag);
        freezeIndex(byKeyword);

        return new RecipeIndexSnapshot(Collections.unmodifiableMap(byId), byOutput, byIngredient, byTag, byKeyword);
    }

    private static void addIndex(Map<String, List<String>> index, String key, String recipeId) {
        index.computeIfAbsent(key, k -> new ArrayList<>()).add(recipeId);
    }

    private static void freezeIndex(Map<String, List<String>> index) {
        for (Map.Entry<String, List<String>> entry : index.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
    }
}
