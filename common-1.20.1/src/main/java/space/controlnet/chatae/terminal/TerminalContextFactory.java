package space.controlnet.chatae.terminal;

import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.core.terminal.AiTerminalData;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.menu.AiTerminalMenu;

import java.util.Optional;

public final class TerminalContextFactory {
    private TerminalContextFactory() {
    }

    public static Optional<TerminalContext> fromPlayer(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        if (!(player.containerMenu instanceof AiTerminalMenu menu)) {
            return Optional.empty();
        }
        Optional<AiTerminalHost> host = menu.getHost();
        if (host.isEmpty()) {
            return Optional.empty();
        }
        AiTerminalHost terminal = host.get();
        if (terminal.isRemovedHost()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerTerminalContext(player, terminal));
    }

    private static final class PlayerTerminalContext implements TerminalContext {
        private final ServerPlayer player;
        private final AiTerminalHost terminal;

        private PlayerTerminalContext(ServerPlayer player, AiTerminalHost terminal) {
            this.player = player;
            this.terminal = terminal;
        }

        @Override
        public AiTerminalData.Ae2ListResult listItems(String query, boolean craftableOnly, int limit, String pageToken) {
            return terminal.listItems(query, craftableOnly, limit, pageToken);
        }

        @Override
        public AiTerminalData.Ae2ListResult listCraftables(String query, int limit, String pageToken) {
            return terminal.listCraftables(query, limit, pageToken);
        }

        @Override
        public AiTerminalData.Ae2CraftSimulation simulateCraft(String itemId, long count) {
            return terminal.simulateCraft(player, itemId, count);
        }

        @Override
        public AiTerminalData.Ae2CraftRequest requestCraft(String itemId, long count, String cpuName) {
            return terminal.requestCraft(player, itemId, count, cpuName);
        }

        @Override
        public AiTerminalData.Ae2JobStatus jobStatus(String jobId) {
            return terminal.jobStatus(jobId);
        }

        @Override
        public AiTerminalData.Ae2JobStatus cancelJob(String jobId) {
            return terminal.cancelJob(jobId);
        }
    }
}
