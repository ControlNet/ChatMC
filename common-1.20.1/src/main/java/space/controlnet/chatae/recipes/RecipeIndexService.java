package space.controlnet.chatae.recipes;

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
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chatae-recipe-index");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<RecipeIndexSnapshot> snapshotRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> indexingRef = new AtomicReference<>();

    public boolean isReady() {
        return snapshotRef.get() != null;
    }

    public Optional<CompletableFuture<Void>> indexingFuture() {
        return Optional.ofNullable(indexingRef.get());
    }

    public void shutdown() {
        indexExecutor.shutdownNow();
    }

    public CompletableFuture<Void> rebuildAsync(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        List<ResourceLocation> recipeIds = recipeManager.getRecipeIds().toList();

        snapshotRef.set(null);

        CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(() -> buildSnapshot(server, recipeManager, recipeIds), indexExecutor)
                .thenAccept(snapshotRef::set);

        indexingRef.set(future);
        return future;
    }

    public RecipeSearchResult search(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            return new RecipeSearchResult(List.of(), Optional.of("0"));
        }

        int safeLimit = Math.max(1, Math.min(limit, 100));
        int offset = pageToken.flatMap(RecipeIndexService::parseOffset).orElse(0);

        List<String> candidates = resolveCandidates(snapshot, query, filters);
        List<RecipeSummary> results = new ArrayList<>(Math.min(candidates.size(), safeLimit));

        for (int i = offset; i < candidates.size() && results.size() < safeLimit; i++) {
            RecipeSummary summary = snapshot.byId().get(candidates.get(i));
            if (summary != null) {
                results.add(summary);
            }
        }

        Optional<String> next = (offset + results.size()) < candidates.size()
                ? Optional.of(Integer.toString(offset + results.size()))
                : Optional.empty();

        return new RecipeSearchResult(results, next);
    }

    public Optional<RecipeSummary> get(String recipeId) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.byId().get(recipeId));
    }

    private static Optional<Integer> parseOffset(String token) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static List<String> resolveCandidates(RecipeIndexSnapshot snapshot, String query, RecipeSearchFilters filters) {
        Optional<String> output = filters.outputItemId();
        Optional<String> ingredient = filters.ingredientItemId();
        Optional<String> tag = filters.tagId();

        Set<String> candidateSet = null;

        if (output.isPresent()) {
            candidateSet = new HashSet<>(snapshot.byOutputItemId().getOrDefault(output.get(), List.of()));
        }

        if (ingredient.isPresent()) {
            Set<String> s = new HashSet<>(snapshot.byIngredientItemId().getOrDefault(ingredient.get(), List.of()));
            candidateSet = candidateSet == null ? s : intersect(candidateSet, s);
        }

        if (tag.isPresent()) {
            Set<String> s = new HashSet<>(snapshot.byTagId().getOrDefault(tag.get(), List.of()));
            candidateSet = candidateSet == null ? s : intersect(candidateSet, s);
        }

        List<String> tokens = tokenize(query);
        Map<String, Integer> scores = new HashMap<>();
        if (!tokens.isEmpty()) {
            for (String token : tokens) {
                for (String id : snapshot.byKeyword().getOrDefault(token, List.of())) {
                    scores.merge(id, 1, Integer::sum);
                }
            }

            Set<String> tokenSet = scores.keySet();
            candidateSet = candidateSet == null ? new HashSet<>(tokenSet) : intersect(candidateSet, tokenSet);
        }

        if (candidateSet == null) {
            candidateSet = new HashSet<>(snapshot.byId().keySet());
        }

        String modId = filters.modId().orElse(null);
        String typeId = filters.recipeType().orElse(null);

        List<String> candidates = new ArrayList<>(candidateSet.size());
        for (String id : candidateSet) {
            RecipeSummary summary = snapshot.byId().get(id);
            if (summary == null) {
                continue;
            }

            if (modId != null) {
                int sep = id.indexOf(':');
                if (sep <= 0 || !id.substring(0, sep).equals(modId)) {
                    continue;
                }
            }

            if (typeId != null && !typeId.equals(summary.recipeType())) {
                continue;
            }

            candidates.add(id);
        }

        candidates.sort(Comparator
                .comparingInt((String id) -> scores.getOrDefault(id, 0)).reversed()
                .thenComparing(id -> id));

        return candidates;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(Math.min(a.size(), b.size()));
        for (String x : a) {
            if (b.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    private static List<String> tokenize(String query) {
        if (query == null) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] parts = normalized.split("[^a-z0-9_:/.-]+",
                -1);

        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
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

            for (String token : tokenize(outputItemId)) {
                addIndex(byKeyword, token, recipeId);
            }

            String displayName = result.getHoverName().getString();
            for (String token : tokenize(displayName)) {
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
