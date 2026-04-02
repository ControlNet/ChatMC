package space.controlnet.mineagent.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;
import space.controlnet.mineagent.common.gametest.ServerThreadConfinementGameTestScenarios;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagent")
public final class ServerThreadConfinementGameTest {
    private ServerThreadConfinementGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent")
    public static void asyncToolInvocationMarshalsToServerThread(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ServerThreadConfinementGameTestScenarios.asyncToolInvocationMarshalsToServerThread(
                        helper,
                        ServerThreadConfinementGameTest::createPlayer
                ));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent_task9_timeout", timeoutTicks = 2400)
    public static void timeoutAndFailureContractsRemainStableUnderForcedDelay(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ServerThreadConfinementGameTestScenarios.timeoutAndFailureContractsRemainStableUnderForcedDelay(
                        helper,
                        ServerThreadConfinementGameTest::createPlayer
                ));
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
