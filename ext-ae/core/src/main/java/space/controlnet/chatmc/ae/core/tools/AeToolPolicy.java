package space.controlnet.chatmc.ae.core.tools;

import space.controlnet.chatmc.core.policy.PolicyDecision;
import space.controlnet.chatmc.core.policy.RiskLevel;

import java.util.Optional;

public final class AeToolPolicy {
    private static final long MAX_CRAFT_COUNT = 10000L;

    private AeToolPolicy() {
    }

    public static RiskLevel classifyRisk(String toolName) {
        return switch (toolName) {
            case "ae.request_craft", "ae.job_cancel" -> RiskLevel.SAFE_MUTATION;
            default -> RiskLevel.READ_ONLY;
        };
    }

    public static PolicyDecision policyFor(RiskLevel risk) {
        return switch (risk) {
            case READ_ONLY -> PolicyDecision.AUTO_APPROVE;
            case SAFE_MUTATION -> PolicyDecision.REQUIRE_APPROVAL;
            case DANGEROUS_MUTATION -> PolicyDecision.DENY;
        };
    }

    public static boolean isValidCraftCount(long count) {
        return count > 0 && count <= MAX_CRAFT_COUNT;
    }

    public static long getMaxCraftCount() {
        return MAX_CRAFT_COUNT;
    }

    public static Optional<String> normalize(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
