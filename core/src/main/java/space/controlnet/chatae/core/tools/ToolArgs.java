package space.controlnet.chatae.core.tools;

/**
 * Tool argument DTOs for tool calls.
 */
public final class ToolArgs {
    private ToolArgs() {
    }

    public record RecipeSearchArgs(
            String query,
            String pageToken,
            int limit,
            String modId,
            String recipeType,
            String outputItemId,
            String ingredientItemId,
            String tagId
    ) {
    }

    public record RecipeGetArgs(String recipeId) {
    }

    public record Ae2ListArgs(String query, boolean craftableOnly, int limit, String pageToken) {
    }

    public record Ae2CraftArgs(String itemId, long count, String cpuName) {
    }

    public record Ae2JobArgs(String jobId) {
    }
}
