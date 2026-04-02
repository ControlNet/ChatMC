package space.controlnet.mineagent.common.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ToolOutputRendererRegistryRegressionTest {
    @Test
    void task18_toolRendererRegistry_registerAndClear_areDeterministic() {
        ToolOutputRendererRegistry.clear();
        ToolOutputRenderer first = new CountingRenderer(true, null);
        ToolOutputRenderer second = new CountingRenderer(true, List.of("rendered by second"));

        ToolOutputRendererRegistry.register(null);
        ToolOutputRendererRegistry.register(first);
        ToolOutputRendererRegistry.register(first);
        ToolOutputRendererRegistry.register(second);

        assertEquals("task18/registry/try-render-null", null, ToolOutputRendererRegistry.tryRender(null));
        assertEquals("task18/registry/fallthrough", List.of("rendered by second"),
                ToolOutputRendererRegistry.tryRender(parseObject("{\"recipeId\":\"demo\"}")));

        ToolOutputRendererRegistry.clear();
        assertEquals("task18/registry/clear", null, ToolOutputRendererRegistry.tryRender(parseObject("{\"recipeId\":\"demo\"}")));
    }

    @Test
    void task18_toolRendererRegistry_skipsNonRenderingCandidates_beforeReturningMatch() {
        ToolOutputRendererRegistry.clear();
        AtomicInteger firstCanRenderCalls = new AtomicInteger();
        AtomicInteger secondRenderCalls = new AtomicInteger();

        ToolOutputRendererRegistry.register(new ToolOutputRenderer() {
            @Override
            public boolean canRender(JsonObject output) {
                firstCanRenderCalls.incrementAndGet();
                return false;
            }

            @Override
            public List<String> render(JsonObject output) {
                throw new AssertionError("task18/registry/first-render-should-not-run");
            }
        });
        ToolOutputRendererRegistry.register(new ToolOutputRenderer() {
            @Override
            public boolean canRender(JsonObject output) {
                return true;
            }

            @Override
            public List<String> render(JsonObject output) {
                secondRenderCalls.incrementAndGet();
                return List.of("matched");
            }
        });

        assertEquals("task18/registry/matched-lines", List.of("matched"),
                ToolOutputRendererRegistry.tryRender(parseObject("{\"outputItemId\":\"bad output\"}")));
        assertEquals("task18/registry/first-can-render-count", 1, firstCanRenderCalls.get());
        assertEquals("task18/registry/second-render-count", 1, secondRenderCalls.get());
    }

    private static JsonObject parseObject(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private record CountingRenderer(boolean canRender, List<String> lines) implements ToolOutputRenderer {
        @Override
        public boolean canRender(JsonObject output) {
            return canRender;
        }

        @Override
        public List<String> render(JsonObject output) {
            return lines;
        }
    }
}
