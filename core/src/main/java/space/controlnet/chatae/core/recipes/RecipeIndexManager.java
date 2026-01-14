package space.controlnet.chatae.core.recipes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Manages the recipe index lifecycle.
 * This class handles async indexing coordination without MC dependencies.
 */
public final class RecipeIndexManager {
    private final AtomicReference<ExecutorService> indexExecutorRef = new AtomicReference<>();
    private final AtomicReference<RecipeIndexSnapshot> snapshotRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> indexingRef = new AtomicReference<>();

    /**
     * Checks if the index is ready for queries.
     */
    public boolean isReady() {
        return snapshotRef.get() != null;
    }

    /**
     * Gets the current indexing future, if any.
     */
    public Optional<CompletableFuture<Void>> indexingFuture() {
        return Optional.ofNullable(indexingRef.get());
    }

    /**
     * Shuts down the index executor.
     */
    public void shutdown() {
        ExecutorService executor = indexExecutorRef.getAndSet(null);
        if (executor != null) {
            executor.shutdownNow();
        }
        indexingRef.set(null);
    }

    /**
     * Rebuilds the index asynchronously using the provided snapshot builder.
     *
     * @param snapshotBuilder supplier that builds the snapshot (runs on background thread)
     * @return future that completes when indexing is done
     */
    public CompletableFuture<Void> rebuildAsync(Supplier<RecipeIndexSnapshot> snapshotBuilder) {
        snapshotRef.set(null);

        ExecutorService executor = getOrCreateExecutor();
        CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(snapshotBuilder, executor)
                .thenAccept(snapshotRef::set);

        indexingRef.set(future);
        return future;
    }

    /**
     * Searches recipes in the index.
     */
    public RecipeSearchResult search(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        return RecipeSearchAlgorithm.search(snapshot, query, filters, pageToken, limit);
    }

    /**
     * Gets a specific recipe by ID.
     */
    public Optional<RecipeSummary> get(String recipeId) {
        RecipeIndexSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.byId().get(recipeId));
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
}
