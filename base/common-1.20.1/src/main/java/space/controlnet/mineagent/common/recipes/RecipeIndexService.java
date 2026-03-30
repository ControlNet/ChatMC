package space.controlnet.mineagent.common.recipes;

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
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.recipes.RecipeIndexSnapshot;
import space.controlnet.mineagent.core.recipes.RecipeSearchAlgorithm;
import space.controlnet.mineagent.core.recipes.RecipeSearchFilters;
import space.controlnet.mineagent.core.recipes.RecipeSearchResult;
import space.controlnet.mineagent.core.recipes.RecipeSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * MC-specific recipe index service.
 * Delegates index management to core RecipeIndexManager and handles MC recipe extraction.
 */
public final class RecipeIndexService {
    private final RecipeIndexManager indexManager = new RecipeIndexManager();

    public boolean isReady() {
        return indexManager.isReady();
    }

    public Optional<CompletableFuture<Void>> indexingFuture() {
        return indexManager.indexingFuture();
    }

    public void shutdown() {
        indexManager.shutdown();
    }

    public CompletableFuture<Void> rebuildAsync(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        List<ResourceLocation> recipeIds = recipeManager.getRecipeIds().toList();

        return indexManager.rebuildAsync(() -> buildSnapshot(server, recipeManager, recipeIds));
    }

    public RecipeSearchResult search(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit) {
        return indexManager.search(query, filters, pageToken, limit);
    }

    public Optional<RecipeSummary> get(String recipeId) {
        return indexManager.get(recipeId);
    }

    public RecipeSearchResult findByOutput(String itemId, Optional<String> pageToken, int limit) {
        return indexManager.findByOutput(itemId, pageToken, limit);
    }

    public RecipeSearchResult findByIngredient(String itemId, Optional<String> pageToken, int limit) {
        return indexManager.findByIngredient(itemId, pageToken, limit);
    }

    /**
     * Builds a recipe index snapshot from MC recipe data.
     * This method runs on a background thread.
     */
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
