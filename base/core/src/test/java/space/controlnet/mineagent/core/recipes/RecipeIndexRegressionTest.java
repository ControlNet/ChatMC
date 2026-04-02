package space.controlnet.mineagent.core.recipes;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RecipeIndexRegressionTest {
    @Test
    void task17_searchAlgorithm_queryScoringAndTieBreak_areDeterministic() {
        RecipeIndexSnapshot snapshot = sampleSnapshot();

        RecipeSearchResult result = RecipeSearchAlgorithm.search(
                snapshot,
                "Chest wood",
                RecipeSearchFilters.empty(),
                Optional.empty(),
                10
        );

        assertRecipeIds(
                "task17/search/scoring-order",
                List.of(
                        "minecraft:chest",
                        "minecraft:barrel",
                        "modded:chest_upgrade"
                ),
                result
        );
        assertEquals("task17/search/scoring-next-token", Optional.empty(), result.nextPageToken());
    }

    @Test
    void task17_searchAlgorithm_filterIntersectionAndPagination_applyStableBounds() {
        RecipeIndexSnapshot snapshot = sampleSnapshot();
        RecipeSearchFilters filters = new RecipeSearchFilters(
                Optional.of("minecraft"),
                Optional.of("crafting"),
                Optional.empty(),
                Optional.of("minecraft:planks"),
                Optional.of("storage")
        );

        RecipeSearchResult firstPage = RecipeSearchAlgorithm.search(
                snapshot,
                "wood",
                filters,
                Optional.of("not-a-number"),
                1
        );
        RecipeSearchResult secondPage = RecipeSearchAlgorithm.search(
                snapshot,
                "wood",
                filters,
                firstPage.nextPageToken(),
                1
        );

        assertRecipeIds("task17/search/intersection-first-page", List.of("minecraft:barrel"), firstPage);
        assertEquals("task17/search/intersection-first-next", Optional.of("1"), firstPage.nextPageToken());
        assertRecipeIds("task17/search/intersection-second-page", List.of("minecraft:chest"), secondPage);
        assertEquals("task17/search/intersection-second-next", Optional.empty(), secondPage.nextPageToken());
    }

    @Test
    void task17_indexManager_findByOutput_skipsStaleIdsWithoutDuplicatingPagination() {
        RecipeSummary chest = summary("minecraft:chest", "crafting", "minecraft:chest", List.of("minecraft:planks"));
        RecipeIndexSnapshot snapshot = new RecipeIndexSnapshot(
                Map.of(chest.recipeId(), chest),
                Map.of("minecraft:chest", List.of("missing:stale_recipe", chest.recipeId())),
                Map.of(),
                Map.of(),
                Map.of()
        );
        RecipeIndexManager manager = new RecipeIndexManager();
        manager.rebuildAsync(() -> snapshot).join();

        RecipeSearchResult firstPage = manager.findByOutput("minecraft:chest", Optional.empty(), 1);
        RecipeSearchResult terminalPage = manager.findByOutput("minecraft:chest", Optional.of("2"), 1);

        assertRecipeIds("task17/manager/stale-first-page", List.of("minecraft:chest"), firstPage);
        assertEquals("task17/manager/stale-first-next", Optional.empty(), firstPage.nextPageToken());
        assertRecipeIds("task17/manager/stale-terminal-page-empty", List.of(), terminalPage);
        assertEquals("task17/manager/stale-terminal-next", Optional.empty(), terminalPage.nextPageToken());
    }

    @Test
    void task17_searchAlgorithm_negativeAndInvalidPageTokens_fallBackToFirstPage() {
        RecipeIndexSnapshot snapshot = sampleSnapshot();
        RecipeSearchFilters filters = new RecipeSearchFilters(
                Optional.of("minecraft"),
                Optional.of("crafting"),
                Optional.empty(),
                Optional.of("minecraft:planks"),
                Optional.of("storage")
        );

        RecipeSearchResult fromNegativeToken = RecipeSearchAlgorithm.search(
                snapshot,
                "wood",
                filters,
                Optional.of("-1"),
                1
        );
        RecipeSearchResult fromInvalidToken = RecipeSearchAlgorithm.search(
                snapshot,
                "wood",
                filters,
                Optional.of("bad-token"),
                1
        );

        assertRecipeIds("task17/search/negative-token-first-page", List.of("minecraft:barrel"), fromNegativeToken);
        assertEquals("task17/search/negative-token-next", Optional.of("1"), fromNegativeToken.nextPageToken());
        assertRecipeIds("task17/search/invalid-token-first-page", List.of("minecraft:barrel"), fromInvalidToken);
        assertEquals("task17/search/invalid-token-next", Optional.of("1"), fromInvalidToken.nextPageToken());
    }

    @Test
    void task17_manager_notReadySearches_returnTerminalEmptyPages() {
        RecipeIndexManager manager = new RecipeIndexManager();

        RecipeSearchResult search = manager.search("chest", RecipeSearchFilters.empty(), Optional.empty(), 5);
        RecipeSearchResult byOutput = manager.findByOutput("minecraft:chest", Optional.empty(), 5);
        RecipeSearchResult byIngredient = manager.findByIngredient("minecraft:planks", Optional.empty(), 5);

        assertRecipeIds("task17/manager/not-ready/search-empty", List.of(), search);
        assertEquals("task17/manager/not-ready/search-terminal", Optional.empty(), search.nextPageToken());
        assertRecipeIds("task17/manager/not-ready/output-empty", List.of(), byOutput);
        assertEquals("task17/manager/not-ready/output-terminal", Optional.empty(), byOutput.nextPageToken());
        assertRecipeIds("task17/manager/not-ready/ingredient-empty", List.of(), byIngredient);
        assertEquals("task17/manager/not-ready/ingredient-terminal", Optional.empty(), byIngredient.nextPageToken());
    }

    @Test
    void task17_indexManager_rebuildAsync_clearsReadyStateUntilSnapshotCompletes() throws Exception {
        RecipeIndexManager manager = new RecipeIndexManager();
        RecipeIndexSnapshot firstSnapshot = sampleSnapshot();
        manager.rebuildAsync(() -> firstSnapshot).join();
        assertTrue("task17/manager/rebuild/initial-ready", manager.isReady());

        CountDownLatch builderEntered = new CountDownLatch(1);
        CountDownLatch releaseBuilder = new CountDownLatch(1);
        RecipeIndexSnapshot replacementSnapshot = singleRecipeSnapshot("minecraft:hopper");

        CompletableFuture<Void> future = manager.rebuildAsync(() -> {
            builderEntered.countDown();
            await("task17/manager/rebuild/release-builder", releaseBuilder);
            return replacementSnapshot;
        });

        assertTrue("task17/manager/rebuild/builder-entered", builderEntered.await(5, TimeUnit.SECONDS));
        assertTrue("task17/manager/rebuild/indexing-future-present", manager.indexingFuture().isPresent());
        assertEquals("task17/manager/rebuild/indexing-future-same", future, manager.indexingFuture().orElseThrow());
        assertFalse("task17/manager/rebuild/ready-cleared-during-index", manager.isReady());
        assertRecipeIds(
                "task17/manager/rebuild/search-empty-while-indexing",
                List.of(),
                manager.search("hopper", RecipeSearchFilters.empty(), Optional.empty(), 5)
        );

        releaseBuilder.countDown();
        future.get(5, TimeUnit.SECONDS);

        assertTrue("task17/manager/rebuild/ready-after-complete", manager.isReady());
        assertRecipeIds(
                "task17/manager/rebuild/search-uses-replacement-snapshot",
                List.of("minecraft:hopper"),
                manager.search("hopper", RecipeSearchFilters.empty(), Optional.empty(), 5)
        );
    }

    @Test
    void task17_indexManager_rebuildFailure_leavesManagerNotReadyAndFutureExceptional() {
        RecipeIndexManager manager = new RecipeIndexManager();
        manager.rebuildAsync(() -> singleRecipeSnapshot("minecraft:chest")).join();

        CompletableFuture<Void> failed = manager.rebuildAsync(() -> {
            throw new IllegalStateException("task17-rebuild-failure");
        });

        Throwable failure = assertThrows("task17/manager/rebuild-failure/join", Throwable.class, failed::join);

        assertContains("task17/manager/rebuild-failure/cause-message", rootCause(failure).getMessage(), "task17-rebuild-failure");
        assertFalse("task17/manager/rebuild-failure/not-ready", manager.isReady());
        assertTrue("task17/manager/rebuild-failure/future-present", manager.indexingFuture().isPresent());
        assertTrue("task17/manager/rebuild-failure/future-completed-exceptionally",
                manager.indexingFuture().orElseThrow().isCompletedExceptionally());
        assertRecipeIds(
                "task17/manager/rebuild-failure/search-empty",
                List.of(),
                manager.search("chest", RecipeSearchFilters.empty(), Optional.empty(), 5)
        );
    }

    @Test
    void task17_indexManager_shutdown_allowsCleanRebuildWithFreshExecutor() {
        RecipeIndexManager manager = new RecipeIndexManager();
        manager.rebuildAsync(() -> singleRecipeSnapshot("minecraft:chest")).join();

        manager.shutdown();

        assertEquals("task17/manager/shutdown/indexing-future-cleared", Optional.empty(), manager.indexingFuture());

        manager.rebuildAsync(() -> singleRecipeSnapshot("minecraft:hopper")).join();

        assertTrue("task17/manager/shutdown/rebuild-ready", manager.isReady());
        assertRecipeIds(
                "task17/manager/shutdown/rebuild-search",
                List.of("minecraft:hopper"),
                manager.search("hopper", RecipeSearchFilters.empty(), Optional.empty(), 5)
        );
    }

    @Test
    void task20_indexManager_getAndExecutorReuse_coverReadyAndIdleBranches() {
        RecipeIndexManager manager = new RecipeIndexManager();

        manager.shutdown();
        assertEquals("task20/manager/idle-shutdown-clears-future", Optional.empty(), manager.indexingFuture());
        assertEquals("task20/manager/get-before-ready", Optional.empty(), manager.get("minecraft:chest"));

        RecipeIndexSnapshot first = singleRecipeSnapshot("minecraft:chest");
        manager.rebuildAsync(() -> first).join();
        assertEquals("task20/manager/get-existing",
                Optional.of(first.byId().get("minecraft:chest")),
                manager.get("minecraft:chest"));
        assertEquals("task20/manager/get-missing", Optional.empty(), manager.get("minecraft:missing"));

        manager.rebuildAsync(() -> singleRecipeSnapshot("minecraft:barrel")).join();
        assertRecipeIds(
                "task20/manager/executor-reuse-search-updated",
                List.of("minecraft:barrel"),
                manager.search("barrel", RecipeSearchFilters.empty(), Optional.empty(), 5)
        );
    }

    @Test
    void task20_indexManager_findByIngredient_handlesStaleIdsAndOffsetBounds() {
        RecipeSummary barrel = summary("minecraft:barrel", "crafting", "minecraft:barrel", List.of("minecraft:planks"));
        RecipeIndexSnapshot snapshot = new RecipeIndexSnapshot(
                Map.of(barrel.recipeId(), barrel),
                Map.of("minecraft:barrel", List.of(barrel.recipeId())),
                Map.of("minecraft:planks", List.of("missing:stale_recipe", barrel.recipeId())),
                Map.of(),
                Map.of("barrel", List.of(barrel.recipeId()))
        );

        RecipeIndexManager manager = new RecipeIndexManager();
        manager.rebuildAsync(() -> snapshot).join();

        RecipeSearchResult first = manager.findByIngredient("minecraft:planks", Optional.empty(), 1);
        RecipeSearchResult fromNegative = manager.findByIngredient("minecraft:planks", Optional.of("-3"), 1);
        RecipeSearchResult outOfRange = manager.findByIngredient("minecraft:planks", Optional.of("8"), 1);

        assertRecipeIds("task20/manager/ingredient-first", List.of("minecraft:barrel"), first);
        assertEquals("task20/manager/ingredient-first-next", Optional.empty(), first.nextPageToken());
        assertRecipeIds("task20/manager/ingredient-negative-falls-back", List.of("minecraft:barrel"), fromNegative);
        assertRecipeIds("task20/manager/ingredient-out-of-range-empty", List.of(), outOfRange);
        assertEquals("task20/manager/ingredient-out-of-range-next", Optional.empty(), outOfRange.nextPageToken());
    }

    @Test
    void task20_searchAlgorithm_edgeInputs_coverTokenizeIntersectAndMissingSummaries() {
        assertEquals("task20/algorithm/tokenize-null", List.of(), RecipeSearchAlgorithm.tokenize(null));
        assertEquals("task20/algorithm/tokenize-blank", List.of(), RecipeSearchAlgorithm.tokenize("   "));
        assertEquals(
                "task20/algorithm/tokenize-normalized",
                List.of("alpha", "beta/gamma"),
                RecipeSearchAlgorithm.tokenize("  Alpha beta/Gamma  ")
        );

        assertEquals(
                "task20/algorithm/intersect-no-overlap",
                java.util.Set.of(),
                RecipeSearchAlgorithm.intersect(java.util.Set.of("a"), java.util.Set.of("b"))
        );

        RecipeSummary valid = summary("minecraft:valid_recipe", "crafting", "minecraft:stick", List.of("minecraft:planks"));
        RecipeIndexSnapshot missingKeywordReference = new RecipeIndexSnapshot(
                Map.of(valid.recipeId(), valid),
                Map.of("minecraft:stick", List.of(valid.recipeId())),
                Map.of("minecraft:planks", List.of(valid.recipeId())),
                Map.of(),
                Map.of("ghost", List.of("missing:ghost_recipe", valid.recipeId()))
        );

        RecipeSearchResult ghostSearch = RecipeSearchAlgorithm.search(
                missingKeywordReference,
                "ghost",
                RecipeSearchFilters.empty(),
                Optional.empty(),
                10
        );
        assertRecipeIds("task20/algorithm/skip-missing-summary", List.of(valid.recipeId()), ghostSearch);

        RecipeSummary noNamespace = summary("invalidid", "crafting", "minecraft:stone", List.of("minecraft:cobblestone"));
        RecipeSummary namespaced = summary("minecraft:oak_planks", "crafting", "minecraft:oak_planks", List.of("minecraft:log"));
        RecipeIndexSnapshot mixedIds = new RecipeIndexSnapshot(
                orderedMap(
                        noNamespace.recipeId(), noNamespace,
                        namespaced.recipeId(), namespaced
                ),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        RecipeSearchFilters modAndType = new RecipeSearchFilters(
                Optional.of("minecraft"),
                Optional.of("crafting"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertEquals(
                "task20/algorithm/mod-filter-skips-non-namespaced-id",
                List.of("minecraft:oak_planks"),
                RecipeSearchAlgorithm.resolveCandidates(mixedIds, "", modAndType)
        );
    }

    private static RecipeIndexSnapshot sampleSnapshot() {
        RecipeSummary chest = summary("minecraft:chest", "crafting", "minecraft:chest", List.of("minecraft:planks"));
        RecipeSummary barrel = summary("minecraft:barrel", "crafting", "minecraft:barrel", List.of("minecraft:planks", "minecraft:slab"));
        RecipeSummary upgrade = summary("modded:chest_upgrade", "smithing", "modded:chest_upgrade", List.of("minecraft:chest", "minecraft:iron_ingot"));
        RecipeSummary lantern = summary("minecraft:lantern", "smelting", "minecraft:lantern", List.of("minecraft:torch"));

        Map<String, RecipeSummary> byId = orderedMap(
                chest.recipeId(), chest,
                barrel.recipeId(), barrel,
                upgrade.recipeId(), upgrade,
                lantern.recipeId(), lantern
        );

        return new RecipeIndexSnapshot(
                byId,
                orderedMap(
                        "minecraft:chest", List.of(chest.recipeId()),
                        "minecraft:barrel", List.of(barrel.recipeId()),
                        "modded:chest_upgrade", List.of(upgrade.recipeId()),
                        "minecraft:lantern", List.of(lantern.recipeId())
                ),
                orderedMap(
                        "minecraft:planks", List.of(barrel.recipeId(), chest.recipeId()),
                        "minecraft:slab", List.of(barrel.recipeId()),
                        "minecraft:chest", List.of(upgrade.recipeId()),
                        "minecraft:iron_ingot", List.of(upgrade.recipeId()),
                        "minecraft:torch", List.of(lantern.recipeId())
                ),
                orderedMap(
                        "storage", List.of(barrel.recipeId(), chest.recipeId(), upgrade.recipeId()),
                        "metal", List.of(upgrade.recipeId(), lantern.recipeId())
                ),
                orderedMap(
                        "chest", List.of(chest.recipeId(), upgrade.recipeId()),
                        "wood", List.of(chest.recipeId(), barrel.recipeId()),
                        "upgrade", List.of(upgrade.recipeId()),
                        "lantern", List.of(lantern.recipeId())
                )
        );
    }

    private static RecipeIndexSnapshot singleRecipeSnapshot(String recipeId) {
        RecipeSummary summary = summary(recipeId, "crafting", recipeId, List.of("minecraft:planks"));
        return new RecipeIndexSnapshot(
                Map.of(recipeId, summary),
                Map.of(summary.outputItemId(), List.of(recipeId)),
                Map.of("minecraft:planks", List.of(recipeId)),
                Map.of("storage", List.of(recipeId)),
                Map.of(tokenFromId(recipeId), List.of(recipeId))
        );
    }

    private static String tokenFromId(String recipeId) {
        int separator = recipeId.indexOf(':');
        return separator >= 0 ? recipeId.substring(separator + 1) : recipeId;
    }

    private static RecipeSummary summary(String recipeId, String recipeType, String outputItemId, List<String> ingredients) {
        return new RecipeSummary(recipeId, recipeType, outputItemId, 1, ingredients);
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
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(assertionName + " -> timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(assertionName + " -> interrupted", exception);
        }
    }

    private static void assertRecipeIds(String assertionName, List<String> expectedRecipeIds, RecipeSearchResult result) {
        assertEquals(assertionName, expectedRecipeIds,
                result.results().stream().map(RecipeSummary::recipeId).toList());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T assertThrows(String assertionName, Class<T> expectedType, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expectedType.isInstance(throwable)) {
                return expectedType.cast(throwable);
            }
            throw new AssertionError(
                    assertionName + " -> expected " + expectedType.getName() + " but got " + throwable.getClass().getName(),
                    throwable
            );
        }
        throw new AssertionError(assertionName + " -> expected exception " + expectedType.getName());
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
