package space.controlnet.mineagent.ae.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.ae.common.gametest.AeCraftLifecycleIsolationGameTestScenarios;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagentae")
public final class AeCpuUnavailableGameTest {
    private AeCpuUnavailableGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagentae", timeoutTicks = 2400)
    public static void aeCpuTargetedUnavailableCpuBranch(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AeCraftLifecycleIsolationGameTestScenarios.cpuTargetedUnavailableCpuBranch(
                        helper,
                        AeCpuUnavailableGameTest::createPlayer
                ));
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
