package space.controlnet.chatmc.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.chatmc.common.gametest.AgentReliabilityGameTestScenarios;
import space.controlnet.chatmc.common.gametest.GameTestRuntimeLease;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("chatmc")
public final class AgentSystemReliabilityGameTest {
    private AgentSystemReliabilityGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "chatmc_agent_runtime", timeoutTicks = 160)
    public static void agentSystemReliability(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> AgentReliabilityGameTestScenarios.run(helper, AgentSystemReliabilityGameTest::createPlayer));
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, playerName));
    }
}
