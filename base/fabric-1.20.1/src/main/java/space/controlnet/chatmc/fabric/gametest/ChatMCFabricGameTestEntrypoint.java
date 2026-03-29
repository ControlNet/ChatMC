package space.controlnet.chatmc.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ChatMCFabricGameTestEntrypoint {
    public ChatMCFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.commandMenuOpenCloseLifecycleCleanup(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseProposalBindingUnavailable(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.proposalBindingUnavailableApprovalFailsDeterministically(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseIndexingGateRecovery(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.indexingGateRecoveryAcrossReload(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_task8_viewer")
    public static void baseViewerChurnConsistency(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.multiViewerSnapshotConsistencyUnderChurn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseServerThreadConfinement(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.asyncToolInvocationMarshalsToServerThread(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_task9_timeout", timeoutTicks = 2400)
    public static void baseTimeoutFailureContractUnderForcedDelay(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.timeoutAndFailureContractsRemainStableUnderForcedDelay(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc")
    public static void baseToolArgsBoundaryE2e(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.toolArgsBoundaryEndToEnd(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void baseSessionVisibilityDeleteRebindRuntime(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.sessionVisibilityDeleteRebindUnderRuntimeConditions(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "chatmc_runtime")
    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        ChatMCFabricRuntimeGameTests.deletedSessionQueuedAppendDoesNotRecreate(helper);
    }
}
