package space.controlnet.mineagent.common.testing;

/**
 * Central registry of stable scenario IDs shared between legacy tests and future GameTest/integration
 * migrations.
 */
public final class ScenarioIds {
    private ScenarioIds() {
        throw new AssertionError("No instances.");
    }

    /** Scenario covering proposal binding availability checks. */
    public static final String PROPOSAL_BINDING_UNAVAILABLE = "scenario/proposal-binding-unavailable";

    /** Scenario covering indexing gate recovery assertions. */
    public static final String INDEXING_GATE_RECOVERY = "scenario/indexing-gate-recovery";

    /** Scenario covering viewer churn consistency expectations. */
    public static final String VIEWER_CHURN_CONSISTENCY = "scenario/viewer-churn-consistency";

    /** Scenario covering server thread confinement promises. */
    public static final String SERVER_THREAD_CONFINEMENT = "scenario/server-thread-confinement";

    /** Scenario covering tool args boundary end-to-end validation. */
    public static final String TOOL_ARGS_BOUNDARY_E2E = "scenario/tool-args-boundary-e2e";
}
