package space.controlnet.chatmc.core.recipes;

import java.util.Optional;

public record RecipeSearchFilters(
        Optional<String> modId,
        Optional<String> recipeType,
        Optional<String> outputItemId,
        Optional<String> ingredientItemId,
        Optional<String> tagId
) {
    public RecipeSearchFilters {
        modId = modId == null ? Optional.empty() : modId;
        recipeType = recipeType == null ? Optional.empty() : recipeType;
        outputItemId = outputItemId == null ? Optional.empty() : outputItemId;
        ingredientItemId = ingredientItemId == null ? Optional.empty() : ingredientItemId;
        tagId = tagId == null ? Optional.empty() : tagId;
    }

    public static RecipeSearchFilters empty() {
        return new RecipeSearchFilters(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
