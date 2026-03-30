package space.controlnet.mineagent.core.recipes;

import java.util.List;
import java.util.Optional;

public record RecipeSearchResult(List<RecipeSummary> results, Optional<String> nextPageToken) {
    public RecipeSearchResult {
        results = List.copyOf(results);
        nextPageToken = nextPageToken == null ? Optional.empty() : nextPageToken;
    }
}
