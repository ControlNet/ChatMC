package space.controlnet.mineagent.ae.core.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AiTerminalDataNormalizationRegressionTest {
    @Test
    void task16_aeListResult_normalizesNullOptionalsAndDefensivelyCopiesResults() {
        List<AiTerminalData.AeEntry> mutableResults = new ArrayList<>();
        mutableResults.add(new AiTerminalData.AeEntry("ae2:controller", 1L, true));

        AiTerminalData.AeListResult result = new AiTerminalData.AeListResult(mutableResults, null, null);
        mutableResults.add(new AiTerminalData.AeEntry("ae2:fluix_crystal", 2L, false));

        assertEquals("task16/ae-data/list/result-size", 1, result.results().size());
        assertEquals("task16/ae-data/list/next-page-empty", Optional.empty(), result.nextPageToken());
        assertEquals("task16/ae-data/list/error-empty", Optional.empty(), result.error());
        assertThrowsUnsupported("task16/ae-data/list/immutable-results", () -> result.results().add(
                new AiTerminalData.AeEntry("minecraft:stick", 3L, false)));
    }

    @Test
    void task16_aeCraftSimulation_normalizesErrorAndDefensivelyCopiesMissingItems() {
        List<AiTerminalData.AePlanItem> mutableMissing = new ArrayList<>();
        mutableMissing.add(new AiTerminalData.AePlanItem("ae2:logic_processor", 4L));

        AiTerminalData.AeCraftSimulation simulation = new AiTerminalData.AeCraftSimulation(
                "job-1",
                "calculating",
                mutableMissing,
                null
        );
        mutableMissing.clear();

        assertEquals("task16/ae-data/simulation/job-id", "job-1", simulation.jobId());
        assertEquals("task16/ae-data/simulation/missing-size", 1, simulation.missingItems().size());
        assertEquals("task16/ae-data/simulation/error-empty", Optional.empty(), simulation.error());
        assertThrowsUnsupported("task16/ae-data/simulation/immutable-missing", () -> simulation.missingItems().clear());
    }

    @Test
    void task16_aeCraftRequest_andJobStatus_normalizeNullOptionalsAndCopyMissingItems() {
        AiTerminalData.AeCraftRequest request = new AiTerminalData.AeCraftRequest("job-2", "submitted", null);
        List<AiTerminalData.AePlanItem> mutableMissing = new ArrayList<>();
        mutableMissing.add(new AiTerminalData.AePlanItem("ae2:engineering_processor", 2L));

        AiTerminalData.AeJobStatus status = new AiTerminalData.AeJobStatus("job-2", "failed", mutableMissing, null);
        mutableMissing.add(new AiTerminalData.AePlanItem("ae2:calculation_processor", 1L));

        assertEquals("task16/ae-data/request/error-empty", Optional.empty(), request.error());
        assertEquals("task16/ae-data/status/missing-size", 1, status.missingItems().size());
        assertEquals("task16/ae-data/status/error-empty", Optional.empty(), status.error());
        assertThrowsUnsupported("task16/ae-data/status/immutable-missing", () -> status.missingItems().add(
                new AiTerminalData.AePlanItem("minecraft:stick", 1L)));
    }

    private static void assertThrowsUnsupported(String assertionName, Runnable runnable) {
        try {
            runnable.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected UnsupportedOperationException");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
