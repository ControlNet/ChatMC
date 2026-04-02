package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.util.Optional;

public final class AgentLoopResultRegressionTest {
    @Test
    void task21_agentLoopResult_withProposal_setsProposalShape() {
        Proposal proposal = new Proposal(
                "proposal-task21",
                RiskLevel.SAFE_MUTATION,
                "Summarized action",
                new ToolCall("mc.move", "{\"x\":1}"),
                42L,
                ProposalDetails.empty()
        );

        AgentLoopResult result = AgentLoopResult.withProposal(proposal, 3);

        assertTrue("task21/loop-result/proposal-success", result.success());
        assertTrue("task21/loop-result/proposal-present", result.hasProposal());
        assertFalse("task21/loop-result/proposal-no-response", result.hasResponse());
        assertFalse("task21/loop-result/proposal-no-error", result.hasError());
        assertEquals("task21/loop-result/proposal-id", Optional.of(proposal), result.proposal());
        assertEquals("task21/loop-result/proposal-iterations", 3, result.iterationsUsed());
    }

    @Test
    void task21_agentLoopResult_withResponse_setsResponseShape() {
        AgentLoopResult result = AgentLoopResult.withResponse("done", 5);

        assertTrue("task21/loop-result/response-success", result.success());
        assertFalse("task21/loop-result/response-no-proposal", result.hasProposal());
        assertTrue("task21/loop-result/response-present", result.hasResponse());
        assertFalse("task21/loop-result/response-no-error", result.hasError());
        assertEquals("task21/loop-result/response-text", Optional.of("done"), result.finalResponse());
        assertEquals("task21/loop-result/response-iterations", 5, result.iterationsUsed());
    }

    @Test
    void task21_agentLoopResult_withError_setsFailureShape() {
        AgentLoopResult result = AgentLoopResult.withError("broken", 7);

        assertFalse("task21/loop-result/error-failure", result.success());
        assertFalse("task21/loop-result/error-no-proposal", result.hasProposal());
        assertFalse("task21/loop-result/error-no-response", result.hasResponse());
        assertTrue("task21/loop-result/error-present", result.hasError());
        assertEquals("task21/loop-result/error-text", Optional.of("broken"), result.error());
        assertEquals("task21/loop-result/error-iterations", 7, result.iterationsUsed());
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (!value) {
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
