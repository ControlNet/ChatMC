package space.controlnet.mineagent.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;
import space.controlnet.mineagent.common.gametest.SessionLifecycleGameTestScenarios;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagent")
public final class MenuValidityRuntimeGameTest {
    private MenuValidityRuntimeGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent_runtime")
    public static void menuValidityTracksRealHostLivenessConditions(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> SessionLifecycleGameTestScenarios.menuValidityTracksRealHostLivenessConditions(
                        helper,
                        MenuValidityRuntimeGameTest::createPlayer
                ));
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
