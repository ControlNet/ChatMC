package space.controlnet.chatmc.core.recipes;

import java.util.*;

/**
 * Platform-neutral recipe search algorithm.
 * This class contains the pure search logic that operates on RecipeIndexSnapshot.
 */
public final class RecipeSearchAlgorithm {
    private RecipeSearchAlgorithm() {
    }

    /**
     * Searches for recipes in a snapshot based on query and filters.
     */
    public static RecipeSearchResult search(
            RecipeIndexSnapshot snapshot,
            String query,
            RecipeSearchFilters filters,
            Optional<String> pageToken,
            int limit
    ) {
        if (snapshot == null) {
            return new RecipeSearchResult(List.of(), Optional.of("0"));
        }

        int safeLimit = Math.max(1, Math.min(limit, 100));
        int offset = pageToken.flatMap(RecipeSearchAlgorithm::parseOffset).orElse(0);

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

    /**
     * Resolves candidate recipe IDs based on query and filters.
     */
    public static List<String> resolveCandidates(RecipeIndexSnapshot snapshot, String query, RecipeSearchFilters filters) {
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

    /**
     * Tokenizes a query string into searchable tokens.
     */
    public static List<String> tokenize(String query) {
        if (query == null) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] parts = normalized.split("[^a-z0-9_:/.-]+", -1);

        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    /**
     * Computes the intersection of two sets.
     */
    public static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(Math.min(a.size(), b.size()));
        for (String x : a) {
            if (b.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    /**
     * Parses a page token string into an integer offset.
     */
    public static Optional<Integer> parseOffset(String token) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
