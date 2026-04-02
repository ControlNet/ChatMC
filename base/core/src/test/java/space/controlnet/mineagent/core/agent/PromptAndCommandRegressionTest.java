package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.LocalCommandParser;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.util.ItemTagParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PromptAndCommandRegressionTest {
    @Test
    void task18_promptStore_andResolver_applyLocaleFallbackRenderingAndHashing() {
        PromptStore store = new PromptStore();
        store.put(PromptId.AGENT_REASON, "en_us", "Hello {{player}} from {{locale}}");
        store.put(PromptId.AGENT_REASON, "fr_fr", "Bonjour {{player}}");

        PromptResolver resolver = new PromptResolver(store);

        assertEquals(
                "task18/prompt/resolve/exact-locale",
                "Bonjour Alex",
                resolver.resolve(PromptId.AGENT_REASON, "fr_fr", Map.of("player", "Alex"))
        );
        assertEquals(
                "task18/prompt/resolve/fallback-locale",
                "Hello Alex from en_us",
                resolver.resolve(PromptId.AGENT_REASON, "de_de", Map.of("player", "Alex", "locale", "en_us"))
        );
        assertEquals(
                "task18/prompt/resolve/missing-empty",
                "",
                new PromptResolver(new PromptStore()).resolve(PromptId.AGENT_REASON, "en_us", Map.of())
        );

        assertEquals(
                "task18/prompt/hash/value",
                Optional.of("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"),
                PromptResolver.computeHash("hello world")
        );
        assertEquals("task18/prompt/hash/null", Optional.empty(), PromptResolver.computeHash(null));
    }

    @Test
    void task18_promptTemplate_andStore_helpers_coverBlankNullAndReloadPaths() {
        PromptStore store = new PromptStore();
        store.loadAll(Map.of(
                new PromptStore.PromptKey(PromptId.AGENT_REASON.id(), "en_us"), "Default {{player}}",
                new PromptStore.PromptKey(PromptId.AGENT_REASON.id(), "es_es"), "Hola {{player}}"
        ));

        assertEquals(
                "task18/prompt/store/load-all",
                "Hola Bea",
                new PromptResolver(store).resolve(PromptId.AGENT_REASON, "es_es", Map.of("player", "Bea"))
        );

        store.clear();
        assertEquals(
                "task18/prompt/store/clear",
                "",
                new PromptResolver(store).resolve(PromptId.AGENT_REASON, "en_us", Map.of("player", "Bea"))
        );

        assertEquals("task18/prompt/template/blank", "", PromptTemplate.render("   ", Map.of("player", "Bea")));
        assertEquals("task18/prompt/template/null", "", PromptTemplate.render(null, Map.of("player", "Bea")));
        Map<String, String> nullValuedVariables = new HashMap<>();
        nullValuedVariables.put("player", null);
        assertEquals(
                "task18/prompt/template/null-value",
                "Hello ",
                PromptTemplate.render("Hello {{player}}", nullValuedVariables)
        );
    }

    @Test
    void task18_localCommandParser_parsesBothShortcutsAndRejectsUnknownInput() {
        ToolCall findRecipes = LocalCommandParser.parse("  mc.find_recipes minecraft:chest  ");
        ToolCall findUsage = LocalCommandParser.parse("MC.FIND_USAGE minecraft:stick");

        assertEquals("task18/local-command/recipes/tool", "mc.find_recipes", findRecipes.toolName());
        assertContains("task18/local-command/recipes/args", findRecipes.argsJson(), "minecraft:chest");
        assertContains("task18/local-command/recipes/limit", findRecipes.argsJson(), "\"limit\":10");
        assertEquals("task18/local-command/usage/tool", "mc.find_usage", findUsage.toolName());
        assertContains("task18/local-command/usage/args", findUsage.argsJson(), "minecraft:stick");
        assertEquals("task18/local-command/blank", null, LocalCommandParser.parse("   "));
        assertEquals("task18/local-command/unknown", null, LocalCommandParser.parse("say hello"));
    }

    @Test
    void task18_itemTagParser_detectsInvalidTagsAndExtractsAttributesSafely() {
        String text = "Use <item id=\"minecraft:diamond\" display_name=\"Diamond\"> and <item id=\"mod:broken\">.";

        Optional<String> invalid = ItemTagParser.findInvalidItemTag(text, itemId -> itemId.startsWith("minecraft:"));

        assertEquals("task18/item-tag/invalid-id", Optional.of("mod:broken"), invalid);
        assertEquals("task18/item-tag/extract-id", "minecraft:diamond",
                ItemTagParser.extractAttribute("<item id=\"minecraft:diamond\" display_name=\"Diamond\">", "id"));
        assertEquals("task18/item-tag/extract-display", "Diamond",
                ItemTagParser.extractAttribute("<item id=\"minecraft:diamond\" display_name=\"Diamond\">", "display_name"));
        assertEquals("task18/item-tag/extract-missing", null,
                ItemTagParser.extractAttribute("<item id=\"minecraft:diamond\">", "display_name"));
        assertEquals("task18/item-tag/extract-null-tag", null, ItemTagParser.extractAttribute(null, "id"));
        assertEquals("task18/item-tag/contains", true, ItemTagParser.containsItemTags(text));
        assertEquals("task18/item-tag/contains-empty", false, ItemTagParser.containsItemTags("plain text"));
        assertEquals("task18/item-tag/blank-invalid", Optional.empty(), ItemTagParser.findInvalidItemTag(" ", itemId -> false));
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
