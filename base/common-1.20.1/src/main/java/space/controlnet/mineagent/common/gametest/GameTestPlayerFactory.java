package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

@FunctionalInterface
public interface GameTestPlayerFactory {
    ServerPlayer create(GameTestHelper helper, UUID playerId, String playerName);
}
