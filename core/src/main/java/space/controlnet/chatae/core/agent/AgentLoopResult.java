package space.controlnet.chatae.core.agent;

import space.controlnet.chatae.core.proposal.Proposal;

import java.util.Optional;

/**
 * Result of running the agent loop.
 * Contains either a proposal (requiring approval), a final response, or an error.
 */
public record AgentLoopResult(
        boolean success,
        Optional<Proposal> proposal,
        Optional<String> finalResponse,
        Optional<String> error,
        int iterationsUsed
) {
    /**
     * Create a successful result with a proposal requiring approval.
     */
    public static AgentLoopResult withProposal(Proposal proposal, int iterations) {
        return new AgentLoopResult(
                true,
                Optional.of(proposal),
                Optional.empty(),
                Optional.empty(),
                iterations
        );
    }

    /**
     * Create a successful result with a final response.
     */
    public static AgentLoopResult withResponse(String response, int iterations) {
        return new AgentLoopResult(
                true,
                Optional.empty(),
                Optional.of(response),
                Optional.empty(),
                iterations
        );
    }

    /**
     * Create a failed result with an error message.
     */
    public static AgentLoopResult withError(String error, int iterations) {
        return new AgentLoopResult(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.of(error),
                iterations
        );
    }

    /**
     * Check if this result has a proposal requiring approval.
     */
    public boolean hasProposal() {
        return proposal.isPresent();
    }

    /**
     * Check if this result has a final response.
     */
    public boolean hasResponse() {
        return finalResponse.isPresent();
    }

    /**
     * Check if this result has an error.
     */
    public boolean hasError() {
        return error.isPresent();
    }
}
