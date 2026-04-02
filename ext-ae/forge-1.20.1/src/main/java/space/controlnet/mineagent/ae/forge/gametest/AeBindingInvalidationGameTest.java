package space.controlnet.mineagent.ae.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.ae.common.gametest.AeBindingFailureGameTestScenarios;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagentae")
public final class AeBindingInvalidationGameTest {
    private AeBindingInvalidationGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagentae", timeoutTicks = 2400)
    public static void aeBindingInvalidationAfterTerminalRemovalOrWrongSide(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AeBindingFailureGameTestScenarios.bindingInvalidationAfterTerminalRemovalOrWrongSide(
                        helper,
                        AeBindingInvalidationGameTest::createPlayer
                ));
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
