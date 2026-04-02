package space.controlnet.mineagent.common.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.client.render.McToolOutputRenderer;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.core.tools.ToolMessagePayload;
import space.controlnet.mineagent.core.tools.ToolPayload;

import java.util.List;

public final class ToolOutputFormatterRegressionTest {
    @Test
    void task18_toolOutputFormatter_handlesEmptyNullRawAndStatusPayloads() {
        ToolOutputRendererRegistry.clear();

        assertEquals("task18/formatter/empty", List.of(), ToolOutputFormatter.formatLines("   "));
        assertEquals("task18/formatter/null-literal", List.of("No result."), ToolOutputFormatter.formatLines("null"));
        assertEquals("task18/formatter/raw", List.of("plain text"), ToolOutputFormatter.formatLines("plain text"));
        assertEquals(
                "task18/formatter/status-error",
                List.of("Status: queued", "Error: stale-cache"),
                ToolOutputFormatter.formatLines("{\"status\":\"queued\",\"error\":\"stale-cache\"}")
        );
    }

    @Test
    void task18_toolOutputFormatter_mcRendererAndHelpers_renderDeterministicRecipeOutput() {
        ensureMinecraftBootstrap();
        ToolOutputRendererRegistry.clear();
        ToolOutputRendererRegistry.register(new McToolOutputRenderer());

        String resultJson = """
                {
                  "results":[
                    {"recipeId":"minecraft:chest_recipe","recipeType":"crafting","outputItemId":"minecraft:chest","outputCount":1,"ingredientItemIds":["minecraft:planks"]}
                  ],
                  "nextPageToken":"2"
                }
                """;

        assertEquals(
                "task18/formatter/recipe-search",
                List.of("Found 1 recipes.", "Next page: 2"),
                ToolOutputFormatter.formatLines(resultJson)
        );
        assertEquals("task18/formatter/query", "all items", ToolOutputFormatter.formatQuery(null));
        assertEquals("task18/formatter/query-suffix", " (fluix)",
                ToolOutputFormatter.formatQuerySuffix(ToolOutputFormatter.parseJsonObject("{\"query\":\"fluix\"}")));
        assertContains("task18/formatter/craft-target", ToolOutputFormatter.formatCraftTarget(
                ToolOutputFormatter.parseJsonObject("{\"itemId\":\"minecraft:chest\",\"count\":2}")), "minecraft:chest");
        assertEquals("task18/formatter/format-arg", "job-7",
                ToolOutputFormatter.formatArg(ToolOutputFormatter.parseJsonObject("{\"jobId\":\"job-7\"}"), "jobId"));
        assertEquals("task18/formatter/parse-json-invalid", null, ToolOutputFormatter.parseJsonObject("{bad-json"));

        ToolPayload payload = ToolOutputFormatter.parsePayload(ToolMessagePayload.wrap(
                new space.controlnet.mineagent.core.tools.ToolCall("mc.find_recipes", "{\"itemId\":\"minecraft:chest\"}"),
                space.controlnet.mineagent.core.tools.ToolResult.ok(resultJson)
        ));
        assertContains("task18/formatter/parse-payload", payload.outputJson(), "minecraft:chest_recipe");
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task18/formatter/bootstrap", exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
