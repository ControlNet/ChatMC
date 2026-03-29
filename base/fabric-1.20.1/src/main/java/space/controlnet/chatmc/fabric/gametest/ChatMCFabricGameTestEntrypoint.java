package space.controlnet.chatmc.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.chatmc.common.gametest.AgentReliabilityGameTestScenarios;
import space.controlnet.chatmc.common.gametest.GameTestRuntimeLease;

public final class ChatMCFabricGameTestEntrypoint {
    public ChatMCFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.commandMenuOpenCloseLifecycleCleanup(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseProposalBindingUnavailable(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.proposalBindingUnavailableApprovalFailsDeterministically(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseIndexingGateRecovery(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.indexingGateRecoveryAcrossReload(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_task8_viewer")
    public static void baseViewerChurnConsistency(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.multiViewerSnapshotConsistencyUnderChurn(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseServerThreadConfinement(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.asyncToolInvocationMarshalsToServerThread(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_task9_timeout", timeoutTicks = 2400)
    public static void baseTimeoutFailureContractUnderForcedDelay(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.timeoutAndFailureContractsRemainStableUnderForcedDelay(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseToolArgsBoundaryE2e(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.toolArgsBoundaryEndToEnd(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void baseSessionVisibilityDeleteRebindRuntime(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.sessionVisibilityDeleteRebindUnderRuntimeConditions(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ChatMCFabricRuntimeGameTests.deletedSessionQueuedAppendDoesNotRecreate(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_agent_runtime", timeoutTicks = 160)
    public static void baseAgentSystemReliability(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AgentReliabilityGameTestScenarios.run(helper, ChatMCFabricGameTestSupport::createServerPlayer));
    }
}
