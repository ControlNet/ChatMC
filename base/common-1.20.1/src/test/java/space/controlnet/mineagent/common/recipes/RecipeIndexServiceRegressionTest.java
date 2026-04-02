package space.controlnet.mineagent.common.recipes;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.recipes.RecipeIndexSnapshot;
import space.controlnet.mineagent.core.recipes.RecipeSearchFilters;
import space.controlnet.mineagent.core.recipes.RecipeSearchResult;
import space.controlnet.mineagent.core.recipes.RecipeSummary;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RecipeIndexServiceRegressionTest {
    @Test
    void task18_recipeIndexService_initialAndDelegatedQueryState_areDeterministic() {
        RecipeIndexService service = new RecipeIndexService();

        assertFalse("task18/recipe-service/initial-not-ready", service.isReady());
        assertEquals("task18/recipe-service/initial-no-future", Optional.empty(), service.indexingFuture());
        assertRecipeIds("task18/recipe-service/initial-search-empty",
                List.of(), service.search("chest", RecipeSearchFilters.empty(), Optional.empty(), 5));
        assertEquals("task18/recipe-service/initial-get-empty", Optional.empty(), service.get("minecraft:chest_recipe"));

        rebuildManager(service, sampleSnapshot()).join();

        RecipeSearchResult search = service.search("storage", RecipeSearchFilters.empty(), Optional.empty(), 10);
        RecipeSearchResult byOutput = service.findByOutput("minecraft:chest", Optional.empty(), 5);
        RecipeSearchResult byIngredient = service.findByIngredient("minecraft:oak_planks", Optional.empty(), 5);

        assertTrue("task18/recipe-service/ready-after-rebuild", service.isReady());
        assertEquals("task18/recipe-service/get", Optional.of(sampleSnapshot().byId().get("minecraft:chest_recipe")),
                service.get("minecraft:chest_recipe"));
        assertRecipeIds("task18/recipe-service/search-results",
                List.of("minecraft:barrel_recipe", "minecraft:chest_recipe"), search);
        assertRecipeIds("task18/recipe-service/output-results", List.of("minecraft:chest_recipe"), byOutput);
        assertRecipeIds("task18/recipe-service/ingredient-results",
                List.of("minecraft:chest_recipe", "minecraft:barrel_recipe"), byIngredient);
    }

    @Test
    void task18_recipeIndexService_indexingFutureAndShutdown_followManagerState() throws Exception {
        RecipeIndexService service = new RecipeIndexService();
        CountDownLatch builderEntered = new CountDownLatch(1);
        CountDownLatch releaseBuilder = new CountDownLatch(1);

        CompletableFuture<Void> future = rebuildManager(service, () -> {
            builderEntered.countDown();
            await("task18/recipe-service/release-builder", releaseBuilder);
            return sampleSnapshot();
        });

        assertTrue("task18/recipe-service/builder-entered", builderEntered.await(5, TimeUnit.SECONDS));
        assertTrue("task18/recipe-service/future-present", service.indexingFuture().isPresent());
        assertEquals("task18/recipe-service/future-same", future, service.indexingFuture().orElseThrow());

        releaseBuilder.countDown();
        future.get(5, TimeUnit.SECONDS);
        assertTrue("task18/recipe-service/ready-after-join", service.isReady());

        service.shutdown();

        assertEquals("task18/recipe-service/future-cleared-after-shutdown", Optional.empty(), service.indexingFuture());
    }

    private static CompletableFuture<Void> rebuildManager(RecipeIndexService service, RecipeIndexSnapshot snapshot) {
        return rebuildManager(service, () -> snapshot);
    }

    private static CompletableFuture<Void> rebuildManager(RecipeIndexService service, java.util.function.Supplier<RecipeIndexSnapshot> supplier) {
        return manager(service).rebuildAsync(supplier);
    }

    private static RecipeIndexManager manager(RecipeIndexService service) {
        try {
            Field field = RecipeIndexService.class.getDeclaredField("indexManager");
            field.setAccessible(true);
            return (RecipeIndexManager) field.get(service);
        } catch (Exception exception) {
            throw new AssertionError("task18/recipe-service/index-manager", exception);
        }
    }

    private static RecipeIndexSnapshot sampleSnapshot() {
        RecipeSummary chest = new RecipeSummary("minecraft:chest_recipe", "crafting", "minecraft:chest", 1, List.of("minecraft:oak_planks"));
        RecipeSummary barrel = new RecipeSummary("minecraft:barrel_recipe", "crafting", "minecraft:barrel", 1, List.of("minecraft:oak_planks", "minecraft:oak_slab"));
        Map<String, RecipeSummary> byId = orderedMap(
                chest.recipeId(), chest,
                barrel.recipeId(), barrel
        );
        return new RecipeIndexSnapshot(
                byId,
                orderedMap(
                        "minecraft:chest", List.of(chest.recipeId()),
                        "minecraft:barrel", List.of(barrel.recipeId())
                ),
                orderedMap(
                        "minecraft:oak_planks", List.of(chest.recipeId(), barrel.recipeId()),
                        "minecraft:oak_slab", List.of(barrel.recipeId())
                ),
                Map.of("storage", List.of(barrel.recipeId(), chest.recipeId())),
                Map.of("storage", List.of(barrel.recipeId(), chest.recipeId()))
        );
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> orderedMap(Object... entries) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }

    private static void await(String assertionName, CountDownLatch latch) {
        try {
            if (latch.await(5, TimeUnit.SECONDS)) {
                return;
            }
            throw new AssertionError(assertionName + " -> timed out waiting for latch");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError(assertionName + " -> interrupted", interruptedException);
        }
    }

    private static void assertRecipeIds(String assertionName, List<String> expectedIds, RecipeSearchResult result) {
        List<String> actualIds = result.results().stream().map(RecipeSummary::recipeId).toList();
        assertEquals(assertionName, expectedIds, actualIds);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean condition) {
        if (!condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
