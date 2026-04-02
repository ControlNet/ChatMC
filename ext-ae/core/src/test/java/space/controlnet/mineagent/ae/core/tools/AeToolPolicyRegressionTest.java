package space.controlnet.mineagent.ae.core.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.policy.PolicyDecision;
import space.controlnet.mineagent.core.policy.RiskLevel;

import java.util.Optional;

public final class AeToolPolicyRegressionTest {
    @Test
    void task16_classifyRisk_routesMutationAndReadOnlyToolsDeterministically() {
        assertEquals("task16/ae-policy/classify/request-craft", RiskLevel.SAFE_MUTATION,
                AeToolPolicy.classifyRisk("ae.request_craft"));
        assertEquals("task16/ae-policy/classify/job-cancel", RiskLevel.SAFE_MUTATION,
                AeToolPolicy.classifyRisk("ae.job_cancel"));
        assertEquals("task16/ae-policy/classify/list-items", RiskLevel.READ_ONLY,
                AeToolPolicy.classifyRisk("ae.list_items"));
        assertEquals("task16/ae-policy/classify/unknown", RiskLevel.READ_ONLY,
                AeToolPolicy.classifyRisk("ae.unknown_tool"));
    }

    @Test
    void task16_policyFor_mapsRiskLevelsToStableApprovalContracts() {
        assertEquals("task16/ae-policy/policy/read-only", PolicyDecision.AUTO_APPROVE,
                AeToolPolicy.policyFor(RiskLevel.READ_ONLY));
        assertEquals("task16/ae-policy/policy/safe-mutation", PolicyDecision.REQUIRE_APPROVAL,
                AeToolPolicy.policyFor(RiskLevel.SAFE_MUTATION));
        assertEquals("task16/ae-policy/policy/dangerous", PolicyDecision.DENY,
                AeToolPolicy.policyFor(RiskLevel.DANGEROUS_MUTATION));
    }

    @Test
    void task16_isValidCraftCount_enforcesPositiveBoundedRange() {
        assertTrue("task16/ae-policy/count/min-valid", AeToolPolicy.isValidCraftCount(1));
        assertTrue("task16/ae-policy/count/max-valid",
                AeToolPolicy.isValidCraftCount(AeToolPolicy.getMaxCraftCount()));
        assertTrue("task16/ae-policy/count/zero-invalid", !AeToolPolicy.isValidCraftCount(0));
        assertTrue("task16/ae-policy/count/negative-invalid", !AeToolPolicy.isValidCraftCount(-1));
        assertTrue("task16/ae-policy/count/above-max-invalid",
                !AeToolPolicy.isValidCraftCount(AeToolPolicy.getMaxCraftCount() + 1));
    }

    @Test
    void task16_normalize_trimsOnlyByBlanknessAndPreservesNonBlankValues() {
        assertEquals("task16/ae-policy/normalize/null", Optional.empty(), AeToolPolicy.normalize(null));
        assertEquals("task16/ae-policy/normalize/empty", Optional.empty(), AeToolPolicy.normalize(""));
        assertEquals("task16/ae-policy/normalize/blank", Optional.empty(), AeToolPolicy.normalize("   \t"));
        assertEquals("task16/ae-policy/normalize/nonblank", Optional.of("Main CPU"),
                AeToolPolicy.normalize("Main CPU"));
        assertEquals("task16/ae-policy/normalize/preserve-spacing", Optional.of("  cpu-a  "),
                AeToolPolicy.normalize("  cpu-a  "));
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
