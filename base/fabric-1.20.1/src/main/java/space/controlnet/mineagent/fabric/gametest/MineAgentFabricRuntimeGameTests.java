package space.controlnet.mineagent.fabric.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.gametest.IndexingGateRecoveryGameTestScenarios;
import space.controlnet.mineagent.common.gametest.ProposalBindingUnavailableGameTestScenarios;
import space.controlnet.mineagent.common.gametest.ReloadCommandGameTestScenarios;
import space.controlnet.mineagent.common.gametest.ServerThreadConfinementGameTestScenarios;
import space.controlnet.mineagent.common.gametest.SessionLifecycleGameTestScenarios;
import space.controlnet.mineagent.common.gametest.ToolArgsBoundaryGameTestScenarios;
import space.controlnet.mineagent.common.gametest.ViewerChurnConsistencyGameTestScenarios;

import java.util.UUID;

public final class MineAgentFabricRuntimeGameTests {
    private MineAgentFabricRuntimeGameTests() {
    }

    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.commandMenuOpenCloseLifecycleCleanup(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.deletedSessionQueuedAppendDoesNotRecreate(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void reloadCommandSmokeRebuildsRecipeIndex(GameTestHelper helper) {
        ReloadCommandGameTestScenarios.reloadCommandSmokeRebuildsRecipeIndex(helper);
    }

    public static void deleteLastActiveSessionFallbackCreatesNewSession(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.deleteLastActiveSessionFallbackCreatesNewSession(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void menuValidityTracksRealHostLivenessConditions(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.menuValidityTracksRealHostLivenessConditions(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void sessionVisibilitySessionListUpdateCycle(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.sessionVisibilitySessionListUpdateCycle(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void proposalBindingUnavailableApprovalFailsDeterministically(GameTestHelper helper) {
        ProposalBindingUnavailableGameTestScenarios.proposalBindingUnavailableApprovalFailsDeterministically(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void indexingGateRecoveryAcrossReload(GameTestHelper helper) {
        IndexingGateRecoveryGameTestScenarios.indexingGateRecoveryAcrossReload(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void multiViewerSnapshotConsistencyUnderChurn(GameTestHelper helper) {
        ViewerChurnConsistencyGameTestScenarios.multiViewerSnapshotConsistencyUnderChurn(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    public static void asyncToolInvocationMarshalsToServerThread(GameTestHelper helper) {
        ServerThreadConfinementGameTestScenarios.asyncToolInvocationMarshalsToServerThread(
                helper,
                MineAgentFabricGameTestSupport::createServerPlayer
        );
    }

    public static void timeoutAndFailureContractsRemainStableUnderForcedDelay(GameTestHelper helper) {
        ServerThreadConfinementGameTestScenarios.timeoutAndFailureContractsRemainStableUnderForcedDelay(
                helper,
                MineAgentFabricGameTestSupport::createServerPlayer
        );
    }

    public static void toolArgsBoundaryEndToEnd(GameTestHelper helper) {
        ToolArgsBoundaryGameTestScenarios.toolArgsBoundaryEndToEnd(helper);
    }

    public static void sessionVisibilityDeleteRebindUnderRuntimeConditions(GameTestHelper helper) {
        SessionLifecycleGameTestScenarios.sessionVisibilityDeleteRebindUnderRuntimeConditions(
                helper,
                MineAgentFabricRuntimeGameTests::createFakePlayer
        );
    }

    private static ServerPlayer createFakePlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayer.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
