package space.controlnet.chatae.core.tools;

import space.controlnet.chatae.core.recipes.RecipeSearchFilters;
import space.controlnet.chatae.core.recipes.RecipeSearchResult;
import space.controlnet.chatae.core.recipes.RecipeSummary;
import space.controlnet.chatae.core.terminal.TerminalContext;

import java.util.Optional;

/**
 * Context interface for tool execution.
 * Provides access to terminal, recipe index, and logging without MC dependencies.
 */
public interface ToolExecutionContext {

    /**
     * Gets the terminal context for AE2 operations.
     *
     * @return the terminal context, or empty if no terminal is available
     */
    Optional<TerminalContext> getTerminal();

    /**
     * Searches recipes in the index.
     *
     * @param query     search query string
     * @param filters   search filters
     * @param pageToken pagination token
     * @param limit     maximum results to return
     * @return search results
     */
    RecipeSearchResult searchRecipes(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit);

    /**
     * Gets a specific recipe by ID.
     *
     * @param recipeId the recipe ID
     * @return the recipe summary, or empty if not found
     */
    Optional<RecipeSummary> getRecipe(String recipeId);

    /**
     * Checks if the recipe index is ready for queries.
     *
     * @return true if the index is ready
     */
    boolean isRecipeIndexReady();

    /**
     * Logs an error message.
     *
     * @param message the error message
     * @param error   the exception (may be null)
     */
    void logError(String message, Throwable error);
}
