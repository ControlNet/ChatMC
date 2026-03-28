package space.controlnet.chatmc.common.team;

import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatmc.common.ChatMC;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class TeamAccess {
    private TeamAccess() {
    }

    public static boolean isTeamFeatureAvailable() {
        return Platform.isModLoaded("ftbteams");
    }

    public static Optional<String> getTeamId(ServerPlayer player) {
        if (!isTeamFeatureAvailable()) {
            return Optional.empty();
        }
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Method apiGet = apiClass.getMethod("get");
            Object api = apiGet.invoke(null);
            Method managerMethod = apiClass.getMethod("getTeamManager");
            Object manager = managerMethod.invoke(api);
            Method getTeamForPlayer = manager.getClass().getMethod("getTeamForPlayer", UUID.class);
            Object team = getTeamForPlayer.invoke(manager, player.getUUID());
            if (team == null) {
                return Optional.empty();
            }
            Method getId = team.getClass().getMethod("getId");
            Object id = getId.invoke(team);
            return id != null ? Optional.of(id.toString()) : Optional.empty();
        } catch (Throwable t) {
            ChatMC.LOGGER.warn("FTB Teams integration unavailable", t);
            return Optional.empty();
        }
    }
}
