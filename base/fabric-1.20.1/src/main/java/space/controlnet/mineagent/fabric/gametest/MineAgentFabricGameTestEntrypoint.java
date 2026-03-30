package space.controlnet.mineagent.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.mineagent.common.gametest.AgentReliabilityGameTestScenarios;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;

public final class MineAgentFabricGameTestEntrypoint {
    public MineAgentFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.commandMenuOpenCloseLifecycleCleanup(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseProposalBindingUnavailable(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.proposalBindingUnavailableApprovalFailsDeterministically(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseIndexingGateRecovery(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.indexingGateRecoveryAcrossReload(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_task8_viewer")
    public static void baseViewerChurnConsistency(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.multiViewerSnapshotConsistencyUnderChurn(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseServerThreadConfinement(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.asyncToolInvocationMarshalsToServerThread(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_task9_timeout", timeoutTicks = 2400)
    public static void baseTimeoutFailureContractUnderForcedDelay(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.timeoutAndFailureContractsRemainStableUnderForcedDelay(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent")
    public static void baseToolArgsBoundaryE2e(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.toolArgsBoundaryEndToEnd(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void baseSessionVisibilityDeleteRebindRuntime(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.sessionVisibilityDeleteRebindUnderRuntimeConditions(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_runtime")
    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentFabricRuntimeGameTests.deletedSessionQueuedAppendDoesNotRecreate(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mineagent_agent_runtime", timeoutTicks = 160)
    public static void baseAgentSystemReliability(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AgentReliabilityGameTestScenarios.run(helper, MineAgentFabricGameTestSupport::createServerPlayer));
    }
}
