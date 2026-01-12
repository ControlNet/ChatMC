package space.controlnet.chatae.core.tools;

import space.controlnet.chatae.core.policy.PolicyDecision;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral tool policy and proposal building logic.
 */
public final class ToolPolicy {
    private static final long MAX_CRAFT_COUNT = 10000L;

    private ToolPolicy() {
    }

    /**
     * Classifies the risk level of a tool by name.
     */
    public static RiskLevel classifyRisk(String toolName) {
        return switch (toolName) {
            case "ae2.request_craft", "ae2.job_cancel" -> RiskLevel.SAFE_MUTATION;
            default -> RiskLevel.READ_ONLY;
        };
    }

    /**
     * Determines the policy decision for a given risk level.
     */
    public static PolicyDecision policyFor(RiskLevel risk) {
        return switch (risk) {
            case READ_ONLY -> PolicyDecision.AUTO_APPROVE;
            case SAFE_MUTATION, DANGEROUS_MUTATION -> PolicyDecision.REQUIRE_APPROVAL;
        };
    }

    /**
     * Builds a proposal for a tool call based on its risk level.
     * This method accepts pre-parsed arguments to avoid JSON parsing dependencies in core.
     */
    public static Proposal buildProposal(RiskLevel risk, ToolCall call, ProposalBuilder builder) {
        return builder.build(risk, call);
    }

    /**
     * Functional interface for building proposal details from tool call arguments.
     * Implementations in common layer can parse JSON and extract details.
     */
    @FunctionalInterface
    public interface ProposalBuilder {
        Proposal build(RiskLevel risk, ToolCall call);
    }

    /**
     * Validates that a craft count is within acceptable bounds.
     */
    public static boolean isValidCraftCount(long count) {
        return count > 0 && count <= MAX_CRAFT_COUNT;
    }

    /**
     * Gets the maximum allowed craft count.
     */
    public static long getMaxCraftCount() {
        return MAX_CRAFT_COUNT;
    }

    /**
     * Normalizes a string value to an Optional, treating null/blank as empty.
     */
    public static Optional<String> normalize(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
