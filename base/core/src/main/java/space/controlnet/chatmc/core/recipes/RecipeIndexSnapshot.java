package space.controlnet.chatmc.core.recipes;

import java.util.List;
import java.util.Map;

public record RecipeIndexSnapshot(
        Map<String, RecipeSummary> byId,
        Map<String, List<String>> byOutputItemId,
        Map<String, List<String>> byIngredientItemId,
        Map<String, List<String>> byTagId,
        Map<String, List<String>> byKeyword
) {
}
