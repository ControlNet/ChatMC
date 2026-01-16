package space.controlnet.chatae.core.tools;

import space.controlnet.chatae.core.recipes.RecipeSearchResult;
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
     * Finds recipes that craft the given item (JEI "R" behavior).
     *
     * @param itemId    item id to search by output
     * @param pageToken pagination token
     * @param limit     maximum results to return
     * @return search results
     */
    RecipeSearchResult findRecipesForOutput(String itemId, Optional<String> pageToken, int limit);

    /**
     * Finds recipes that use the given item as an ingredient (JEI "U" behavior).
     *
     * @param itemId    item id to search by ingredient
     * @param pageToken pagination token
     * @param limit     maximum results to return
     * @return search results
     */
    RecipeSearchResult findRecipesUsingIngredient(String itemId, Optional<String> pageToken, int limit);

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
