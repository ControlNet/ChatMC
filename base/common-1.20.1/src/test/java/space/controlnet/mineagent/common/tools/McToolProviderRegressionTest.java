package space.controlnet.mineagent.common.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.client.render.McToolOutputRenderer;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.common.recipes.RecipeIndexService;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;
import space.controlnet.mineagent.core.recipes.RecipeIndexSnapshot;
import space.controlnet.mineagent.core.recipes.RecipeSummary;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McToolProviderRegressionTest {
    @Test
    void task18_mcProvider_invalidAndUnknownCalls_returnStableErrors() {
        McToolProvider provider = new McToolProvider();

        ToolOutcome missingTool = provider.execute(Optional.empty(), null, true);
        ToolOutcome unknownTool = provider.execute(Optional.empty(), new ToolCall("mc.unknown", "{}"), true);
        ToolOutcome missingArgs = provider.execute(Optional.empty(), new ToolCall("mc.find_recipes", "{}"), true);
        ToolOutcome invalidItem = provider.execute(Optional.empty(), new ToolCall("mc.find_usage", "{\"itemId\":\"not a valid id\",\"limit\":3}"), true);

        assertErrorCode("task18/mc-provider/missing-tool", missingTool, "invalid_tool");
        assertErrorCode("task18/mc-provider/unknown-tool", unknownTool, "unknown_tool");
        assertErrorCode("task18/mc-provider/missing-args", missingArgs, "invalid_args");
        assertErrorCode("task18/mc-provider/invalid-item", invalidItem, "invalid_item_id");
    }

    @Test
    void task18_mcProvider_indexGateAndSuccessfulRecipeQueries_areDeterministic() {
        ensureMinecraftBootstrap();
        McToolProvider provider = new McToolProvider();
        clearRecipeIndex();

        ToolOutcome notReady = provider.execute(Optional.empty(),
                new ToolCall("mc.find_recipes", "{\"itemId\":\"minecraft:chest\",\"limit\":2}"), true);
        assertErrorCode("task18/mc-provider/not-ready", notReady, "index_not_ready");

        installRecipeSnapshot(sampleSnapshot());

        ToolResult recipes = requireResult("task18/mc-provider/recipes", provider.execute(Optional.empty(),
                new ToolCall("mc.find_recipes", "{\"itemId\":\"minecraft:chest\",\"limit\":2}"), true));
        ToolResult usage = requireResult("task18/mc-provider/usage", provider.execute(Optional.empty(),
                new ToolCall("mc.find_usage", "{\"itemId\":\"minecraft:oak_planks\",\"limit\":2}"), true));

        assertTrue("task18/mc-provider/recipes-success", recipes.success());
        assertContains("task18/mc-provider/recipes-payload", recipes.payloadJson(), "minecraft:chest_recipe");
        assertContains("task18/mc-provider/usage-payload", usage.payloadJson(), "minecraft:barrel_recipe");
        clearRecipeIndex();
    }

    @Test
    void task18_mcProvider_specsAndLookup_exposeExpectedToolsAndRendering() {
        ensureMinecraftBootstrap();
        ToolOutputRendererRegistry.clear();
        ToolOutputRendererRegistry.register(new McToolOutputRenderer());
        McToolProvider provider = new McToolProvider();

        assertEquals("task18/mc-provider/spec-count", 3, provider.specs().size());
        assertEquals("task18/mc-provider/get-known", "mc.find_recipes",
                requireNonNull("task18/mc-provider/get-known-tool", McToolProvider.get("mc.find_recipes")).name());
        assertEquals("task18/mc-provider/get-null", null, McToolProvider.get(null));

        AgentTool tool = requireNonNull("task18/mc-provider/tool-spec", McToolProvider.get("mc.find_recipes"));
        ToolRender render = tool.render(new space.controlnet.mineagent.core.tools.ToolPayload(
                "mc.find_recipes",
                null,
                "{\"itemId\":\"minecraft:chest\"}",
                "{\"results\":[{\"recipeId\":\"minecraft:chest_recipe\",\"outputItemId\":\"minecraft:chest\",\"outputCount\":1,\"ingredientItemIds\":[\"minecraft:planks\"]}],\"nextPageToken\":\"2\"}",
                null
        ));

        requireNonNull("task18/mc-provider/render-summary-key", render.summaryKey());
        assertEquals("task18/mc-provider/render-summary-arg-count", 1, render.summaryArgs().size());
        assertContains("task18/mc-provider/render-summary-arg", render.summaryArgs().get(0), "minecraft:chest");
        assertEquals("task18/mc-provider/render-lines", List.of("Found 1 recipes.", "Next page: 2"), render.lines());
    }

    private static RecipeIndexSnapshot sampleSnapshot() {
        RecipeSummary chest = new RecipeSummary("minecraft:chest_recipe", "crafting", "minecraft:chest", 1, List.of("minecraft:oak_planks"));
        RecipeSummary barrel = new RecipeSummary("minecraft:barrel_recipe", "crafting", "minecraft:barrel", 1, List.of("minecraft:oak_planks", "minecraft:oak_slab"));
        Map<String, RecipeSummary> byId = orderedMap(
                chest.recipeId(), chest,
                barrel.recipeId(), barrel
        );
        return new RecipeIndexSnapshot(
                byId,
                orderedMap(
                        "minecraft:chest", List.of(chest.recipeId()),
                        "minecraft:barrel", List.of(barrel.recipeId())
                ),
                orderedMap(
                        "minecraft:oak_planks", List.of(chest.recipeId(), barrel.recipeId()),
                        "minecraft:oak_slab", List.of(barrel.recipeId())
                ),
                Map.of(),
                Map.of()
        );
    }

    private static void installRecipeSnapshot(RecipeIndexSnapshot snapshot) {
        try {
            recipeIndexManager().rebuildAsync(() -> snapshot).join();
        } catch (Exception exception) {
            throw new AssertionError("task18/mc-provider/install-snapshot", exception);
        }
    }

    private static void clearRecipeIndex() {
        recipeIndexManager().shutdown();
    }

    private static RecipeIndexManager recipeIndexManager() {
        try {
            Field field = RecipeIndexService.class.getDeclaredField("indexManager");
            field.setAccessible(true);
            return (RecipeIndexManager) field.get(MineAgent.RECIPE_INDEX);
        } catch (Exception exception) {
            throw new AssertionError("task18/mc-provider/index-manager", exception);
        }
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task18/mc-provider/bootstrap", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> orderedMap(Object... entries) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }

    private static ToolResult requireResult(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        return requireNonNull(assertionName + "/result", nonNullOutcome.result());
    }

    private static void assertErrorCode(String assertionName, ToolOutcome outcome, String expectedCode) {
        ToolResult result = requireResult(assertionName, outcome);
        assertTrue(assertionName + "/must-fail", !result.success());
        assertEquals(assertionName + "/code", expectedCode, requireNonNull(assertionName + "/error", result.error()).code());
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> expected non-null");
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
