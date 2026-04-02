package space.controlnet.mineagent.common.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

public final class McToolOutputRendererRegressionTest {
    @Test
    void task18_mcRenderer_nullUnknownAndEmptyResults_areDeterministic() {
        McToolOutputRenderer renderer = new McToolOutputRenderer();

        assertFalse("task18/mc-renderer/can-render-null", renderer.canRender(null));
        assertEquals("task18/mc-renderer/render-null", null, renderer.render(null));

        JsonObject unknown = parseObject("{\"status\":\"queued\"}");
        JsonObject emptyResults = parseObject("{\"results\":[]}");

        assertFalse("task18/mc-renderer/can-render-unknown", renderer.canRender(unknown));
        assertEquals("task18/mc-renderer/render-unknown", null, renderer.render(unknown));
        assertFalse("task18/mc-renderer/can-render-empty-results", renderer.canRender(emptyResults));
        assertEquals("task18/mc-renderer/render-empty-results", null, renderer.render(emptyResults));
    }

    @Test
    void task18_mcRenderer_recipeSearchAndSummary_coverPaginationAndIngredientOverflow() {
        McToolOutputRenderer renderer = new McToolOutputRenderer();
        JsonObject searchOutput = parseObject("""
                {
                  "results":[
                    {"recipeId":"recipe-1","outputItemId":"bad output 1","outputCount":1,"ingredientItemIds":["bad ingredient a"]},
                    {"recipeId":"recipe-2","outputItemId":"bad output 2","outputCount":1,"ingredientItemIds":["bad ingredient b"]}
                  ],
                  "nextPageToken":"4"
                }
                """);
        JsonObject summaryOutput = parseObject("""
                {
                  "recipeId":"recipe-9",
                  "recipeType":"crafting",
                  "outputItemId":"bad output item",
                  "outputCount":2,
                  "ingredientItemIds":[
                    "bad ingredient 1",
                    "bad ingredient 2",
                    "bad ingredient 3",
                    "bad ingredient 4",
                    "bad ingredient 5",
                    "bad ingredient 6",
                    "bad ingredient 7"
                  ]
                }
                """);

        List<String> searchLines = renderer.render(searchOutput);
        List<String> summaryLines = renderer.render(summaryOutput);

        assertTrue("task18/mc-renderer/can-render-search", renderer.canRender(searchOutput));
        assertEquals("task18/mc-renderer/search-lines", List.of("Found 2 recipes.", "Next page: 4"), searchLines);
        assertTrue("task18/mc-renderer/can-render-summary", renderer.canRender(summaryOutput));
        assertEquals("task18/mc-renderer/summary-header", "Recipe: 2x bad output item", summaryLines.get(0));
        assertEquals("task18/mc-renderer/summary-type", "Type: crafting", summaryLines.get(1));
        assertEquals("task18/mc-renderer/summary-id", "Id: recipe-9", summaryLines.get(2));
        assertContains("task18/mc-renderer/summary-ingredients", summaryLines.get(3), "+1 more");
    }

    private static JsonObject parseObject(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean condition) {
        if (!condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
