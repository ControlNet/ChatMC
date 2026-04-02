package space.controlnet.mineagent.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.mineagent.common.gametest.AgentReliabilityGameTestScenarios;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;

public final class MineAgentFabricGameTestEntrypoint {
    private static final String FILTER_PROPERTY = "fabric-api.gametest.filter";

    public MineAgentFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "commandMenuOpenCloseLifecycleCleanup")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.commandMenuOpenCloseLifecycleCleanup(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseReloadCommandSmoke(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "baseReloadCommandSmoke")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.reloadCommandSmokeRebuildsRecipeIndex(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseDeleteLastActiveSessionFallback(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "baseDeleteLastActiveSessionFallback")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.deleteLastActiveSessionFallbackCreatesNewSession(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseMenuValidityRuntime(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "baseMenuValidityRuntime")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.menuValidityTracksRealHostLivenessConditions(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseSessionVisibilitySessionListCycle(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "baseSessionVisibilitySessionListCycle")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.sessionVisibilitySessionListUpdateCycle(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseProposalBindingUnavailable(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent", "baseProposalBindingUnavailable")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.proposalBindingUnavailableApprovalFailsDeterministically(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseIndexingGateRecovery(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent", "baseIndexingGateRecovery")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.indexingGateRecoveryAcrossReload(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_task8_viewer")
    public static void baseViewerChurnConsistency(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_task8_viewer", "baseViewerChurnConsistency")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.multiViewerSnapshotConsistencyUnderChurn(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseServerThreadConfinement(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent", "baseServerThreadConfinement")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.asyncToolInvocationMarshalsToServerThread(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_task9_timeout", timeoutTicks = 2400)
    public static void baseTimeoutFailureContractUnderForcedDelay(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_task9_timeout", "baseTimeoutFailureContractUnderForcedDelay")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.timeoutAndFailureContractsRemainStableUnderForcedDelay(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseToolArgsBoundaryE2e(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent", "baseToolArgsBoundaryE2e")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.toolArgsBoundaryEndToEnd(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseSessionVisibilityDeleteRebindRuntime(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "baseSessionVisibilityDeleteRebindRuntime")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.sessionVisibilityDeleteRebindUnderRuntimeConditions(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_runtime", "deletedSessionQueuedAppendDoesNotRecreate")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.deletedSessionQueuedAppendDoesNotRecreate(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_agent_runtime", timeoutTicks = 160)
    public static void baseAgentSystemReliability(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "mineagent_agent_runtime", "baseAgentSystemReliability")) {
            return;
        }
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AgentReliabilityGameTestScenarios.run(helper, MineAgentFabricGameTestSupport::createServerPlayer));
    }

    private static boolean skipWhenFiltered(GameTestHelper helper, String... selectors) {
        String configuredFilter = System.getProperty(FILTER_PROPERTY);
        if (configuredFilter == null) {
            return false;
        }

        String normalizedFilter = configuredFilter.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedFilter.isEmpty()) {
            return false;
        }

        for (String selector : selectors) {
            String normalizedSelector = selector.toLowerCase(java.util.Locale.ROOT);
            if (normalizedSelector.contains(normalizedFilter) || normalizedFilter.contains(normalizedSelector)) {
                return false;
            }
        }

        helper.succeed();
        return true;
    }
}
