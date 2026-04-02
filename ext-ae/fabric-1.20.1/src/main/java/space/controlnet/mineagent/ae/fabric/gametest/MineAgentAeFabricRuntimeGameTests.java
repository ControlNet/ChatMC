package space.controlnet.mineagent.ae.fabric.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.mineagent.ae.common.gametest.AeBindingFailureGameTestScenarios;
import space.controlnet.mineagent.ae.common.gametest.AeCraftLifecycleIsolationGameTestScenarios;

public final class MineAgentAeFabricRuntimeGameTests {
    private MineAgentAeFabricRuntimeGameTests() {
    }

    public static void craftLifecycleIsolation(GameTestHelper helper) {
        AeCraftLifecycleIsolationGameTestScenarios.craftLifecycleIsolation(
                helper,
                (gameTestHelper, playerId, playerName) -> FakePlayer.get(
                        gameTestHelper.getLevel(),
                        new GameProfile(playerId, playerName)
                )
        );
    }

    public static void boundTerminalApprovalFailsWhenAeBindingUnavailable(GameTestHelper helper) {
        AeBindingFailureGameTestScenarios.boundTerminalApprovalFailsWhenAeBindingUnavailable(
                helper,
                (gameTestHelper, playerId, playerName) -> FakePlayer.get(
                        gameTestHelper.getLevel(),
                        new GameProfile(playerId, playerName)
                )
        );
    }

    public static void boundTerminalApprovalSuccessHandoff(GameTestHelper helper) {
        AeBindingFailureGameTestScenarios.boundTerminalApprovalSuccessHandoff(
                helper,
                space.controlnet.mineagent.fabric.gametest.MineAgentFabricGameTestSupport::createServerPlayer
        );
    }

    public static void terminalTeardownClearsLiveJobs(GameTestHelper helper) {
        AeCraftLifecycleIsolationGameTestScenarios.terminalTeardownClearsLiveJobs(
                helper,
                (gameTestHelper, playerId, playerName) -> FakePlayer.get(
                        gameTestHelper.getLevel(),
                        new GameProfile(playerId, playerName)
                )
        );
    }

    public static void bindingInvalidationAfterTerminalRemovalOrWrongSide(GameTestHelper helper) {
        AeBindingFailureGameTestScenarios.bindingInvalidationAfterTerminalRemovalOrWrongSide(
                helper,
                space.controlnet.mineagent.fabric.gametest.MineAgentFabricGameTestSupport::createServerPlayer
        );
    }

    public static void cpuTargetedUnavailableCpuBranch(GameTestHelper helper) {
        AeCraftLifecycleIsolationGameTestScenarios.cpuTargetedUnavailableCpuBranch(
                helper,
                (gameTestHelper, playerId, playerName) -> FakePlayer.get(
                        gameTestHelper.getLevel(),
                        new GameProfile(playerId, playerName)
                )
        );
    }
}
