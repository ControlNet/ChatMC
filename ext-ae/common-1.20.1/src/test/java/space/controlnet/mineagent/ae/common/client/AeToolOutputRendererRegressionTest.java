package space.controlnet.mineagent.ae.common.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

public final class AeToolOutputRendererRegressionTest {
    @Test
    void task19_canRender_acceptsAeListAndJobPayloads_only() {
        AeToolOutputRenderer renderer = new AeToolOutputRenderer();

        assertFalse("task19/can-render/null", renderer.canRender(null));
        assertFalse("task19/can-render/unknown-shape", renderer.canRender(json("{\"foo\":1}")));
        assertFalse("task19/can-render/non-ae-results", renderer.canRender(json("{\"results\":[{\"itemId\":\"ae2:fluix\"}]}")));

        assertTrue("task19/can-render/ae-results",
                renderer.canRender(json("{\"results\":[{\"itemId\":\"ae2:fluix\",\"amount\":3}]}")));
        assertTrue("task19/can-render/job-status", renderer.canRender(json("{\"jobId\":\"job-7\"}")));
    }

    @Test
    void task19_renderAeList_requiresAeShape_forSafeRenderPath() {
        AeToolOutputRenderer renderer = new AeToolOutputRenderer();

        assertNull("task19/ae-list/empty-results-not-rendered", renderer.render(json("{\"results\":[]}")));
        assertNull("task19/ae-list/non-ae-results-not-rendered",
                renderer.render(json("{\"results\":[{\"itemId\":\"ae2:fluix\"}],\"nextPageToken\":\"x\",\"error\":\"y\"}")));
    }

    @Test
    void task19_renderJobStatus_formatsHeaderStatusAndError_withoutItemFormattingPath() {
        AeToolOutputRenderer renderer = new AeToolOutputRenderer();
        JsonObject payload = json("""
                {
                  "jobId": "job-42",
                  "status": "queued",
                  "missingItems": [],
                  "error": "network"
                }
                """);

        List<String> lines = renderer.render(payload);

        assertEquals("task19/job/header", "Job job-42 — queued", lines.get(0));
        assertEquals("task19/job/error", "Error: network", lines.get(1));
    }

    @Test
    void task19_render_invalidOrUnsupportedPayloads_areStable() {
        AeToolOutputRenderer renderer = new AeToolOutputRenderer();

        assertNull("task19/render/null", renderer.render(null));
        assertNull("task19/render/unsupported", renderer.render(json("{\"foo\":\"bar\"}")));

        JsonObject malformedList = json("{\"results\":[1,2,3],\"nextPageToken\":\"n\"}");
        assertNull("task19/render/non-object-results-not-ae-list", renderer.render(malformedList));

        JsonObject malformedJob = json("{\"jobId\":\"job-1\",\"status\":\"\",\"error\":\"\"}" );
        List<String> jobLines = renderer.render(malformedJob);
        assertEquals("task19/render/malformed-job-header", "Job job-1", jobLines.get(0));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
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

    private static void assertNull(String assertionName, Object value) {
        if (value == null) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected null, actual: " + value);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
