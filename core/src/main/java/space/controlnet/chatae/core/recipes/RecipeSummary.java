package space.controlnet.chatae.core.recipes;

import java.util.List;

public record RecipeSummary(
        String recipeId,
        String recipeType,
        String outputItemId,
        int outputCount,
        List<String> ingredientItemIds
) {
    public RecipeSummary {
        ingredientItemIds = List.copyOf(ingredientItemIds);
    }
}
