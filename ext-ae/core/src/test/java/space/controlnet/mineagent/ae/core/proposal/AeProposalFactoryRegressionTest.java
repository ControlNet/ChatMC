package space.controlnet.mineagent.ae.core.proposal;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.tools.ToolCall;

public final class AeProposalFactoryRegressionTest {
    @Test
    void task15_requestCraftProposal_populatesSummaryAndCpuHintDetailsDeterministically() {
        Proposal proposal = AeProposalFactory.build(
                RiskLevel.SAFE_MUTATION,
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":3,\"cpuName\":\"Main CPU\"}")
        );

        assertEquals("task15/ae-proposal/request/risk", RiskLevel.SAFE_MUTATION, proposal.riskLevel());
        assertEquals("task15/ae-proposal/request/summary", "Craft 3 ae2:controller", proposal.summary());
        assertEquals("task15/ae-proposal/request/action", "Craft", proposal.details().action());
        assertEquals("task15/ae-proposal/request/item-id", "ae2:controller", proposal.details().itemId());
        assertEquals("task15/ae-proposal/request/count", 3L, proposal.details().count());
        assertContains("task15/ae-proposal/request/note-cpu", proposal.details().note(), "CPU hint: Main CPU.");
        assertContains("task15/ae-proposal/request/note-feasibility", proposal.details().note(),
                "Feasibility: unknown (run ae.simulate_craft).");
    }

    @Test
    void task15_requestCraftProposal_withoutCpuHint_usesStableFallbackNote() {
        Proposal proposal = AeProposalFactory.build(
                RiskLevel.SAFE_MUTATION,
                new ToolCall("ae.request_craft", "{\"itemId\":\"minecraft:stick\",\"count\":1}")
        );

        assertEquals("task15/ae-proposal/request-no-cpu/summary", "Craft 1 minecraft:stick", proposal.summary());
        assertEquals("task15/ae-proposal/request-no-cpu/item-id", "minecraft:stick", proposal.details().itemId());
        assertEquals("task15/ae-proposal/request-no-cpu/count", 1L, proposal.details().count());
        assertTrue("task15/ae-proposal/request-no-cpu/no-cpu-hint",
                !proposal.details().note().contains("CPU hint:"));
        assertContains("task15/ae-proposal/request-no-cpu/cancel-guidance", proposal.details().note(),
                "Cancel after submit with ae.job_cancel <jobId>.");
    }

    @Test
    void task15_cancelJobProposal_usesStableSummaryAndDetails() {
        Proposal proposal = AeProposalFactory.build(
                RiskLevel.DANGEROUS_MUTATION,
                new ToolCall("ae.job_cancel", "{\"jobId\":\"job-42\"}")
        );

        assertEquals("task15/ae-proposal/cancel/risk", RiskLevel.DANGEROUS_MUTATION, proposal.riskLevel());
        assertEquals("task15/ae-proposal/cancel/summary", "Cancel job job-42", proposal.summary());
        assertEquals("task15/ae-proposal/cancel/action", "Cancel job", proposal.details().action());
        assertEquals("task15/ae-proposal/cancel/item-id", "job-42", proposal.details().itemId());
        assertEquals("task15/ae-proposal/cancel/count", 0L, proposal.details().count());
        assertEquals("task15/ae-proposal/cancel/note", "Stops an active crafting job.", proposal.details().note());
    }

    @Test
    void task15_unknownAeTool_preservesGenericSummaryAndEmptyDetails() {
        Proposal proposal = AeProposalFactory.build(
                RiskLevel.READ_ONLY,
                new ToolCall("ae.list_items", "{\"query\":\"fluix\"}")
        );

        assertEquals("task15/ae-proposal/unknown/summary", "ae.list_items", proposal.summary());
        assertEquals("task15/ae-proposal/unknown/action", "", proposal.details().action());
        assertEquals("task15/ae-proposal/unknown/item-id", "", proposal.details().itemId());
        assertEquals("task15/ae-proposal/unknown/count", 0L, proposal.details().count());
        assertEquals("task15/ae-proposal/unknown/note", "", proposal.details().note());
    }

    private static void assertContains(String assertionName, String actual, String expectedSubstring) {
        if (actual != null && actual.contains(expectedSubstring)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + expectedSubstring + " in: " + actual);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }
}
