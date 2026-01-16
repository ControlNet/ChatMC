package space.controlnet.chatae.common.tools;

import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.recipes.RecipeSearchResult;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolExecutionContext;
import space.controlnet.chatae.core.tools.ToolExecutor;
import space.controlnet.chatae.core.tools.ToolOutcome;

import java.util.Optional;

/**
 * Routes tool calls to the appropriate handlers.
 * Implements {@link ToolExecutionContext} to provide MC-specific dependencies.
 */
public final class ToolRouter implements ToolExecutionContext {
    private final Optional<TerminalContext> terminal;

    private ToolRouter(Optional<TerminalContext> terminal) {
        this.terminal = terminal;
    }

    /**
     * Executes a tool call with the given terminal context.
     *
     * @param terminal the terminal context for AE2 operations
     * @param call     the tool call to execute
     * @param approved whether the call has been pre-approved
     * @return the outcome of the tool execution
     */
    public static ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        ToolRouter ctx = new ToolRouter(terminal);
        return ToolExecutor.execute(ctx, call, approved);
    }

    @Override
    public Optional<TerminalContext> getTerminal() {
        return terminal;
    }

    @Override
    public RecipeSearchResult findRecipesForOutput(String itemId, Optional<String> pageToken, int limit) {
        return ChatAE.RECIPE_INDEX.findByOutput(itemId, pageToken, limit);
    }

    @Override
    public RecipeSearchResult findRecipesUsingIngredient(String itemId, Optional<String> pageToken, int limit) {
        return ChatAE.RECIPE_INDEX.findByIngredient(itemId, pageToken, limit);
    }

    @Override
    public boolean isRecipeIndexReady() {
        return ChatAE.RECIPE_INDEX.isReady();
    }

    @Override
    public boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) {
            return false;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
    }

    @Override
    public void logError(String message, Throwable error) {
        ChatAE.LOGGER.error(message, error);
    }
}
