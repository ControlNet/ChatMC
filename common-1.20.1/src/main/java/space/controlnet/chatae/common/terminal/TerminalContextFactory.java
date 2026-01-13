package space.controlnet.chatae.common.terminal;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import space.controlnet.chatae.common.menu.AiTerminalMenu;
import space.controlnet.chatae.core.session.TerminalBinding;
import space.controlnet.chatae.core.terminal.AiTerminalData;
import space.controlnet.chatae.core.terminal.TerminalContext;

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

    public static Optional<TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding) {
        if (player == null || binding == null) {
            return Optional.empty();
        }
        if (player.getServer() == null) {
            return Optional.empty();
        }

        ResourceLocation id;
        try {
            id = new ResourceLocation(binding.dimensionId());
        } catch (Exception ignored) {
            return Optional.empty();
        }

        ResourceKey<net.minecraft.world.level.Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = player.getServer().getLevel(dimensionKey);
        if (level == null) {
            return Optional.empty();
        }

        BlockPos pos = new BlockPos(binding.x(), binding.y(), binding.z());
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return Optional.empty();
        }

        Optional<AiTerminalHost> terminal = resolveHostFromBinding(be, binding);
        if (terminal.isEmpty()) {
            return Optional.empty();
        }
        if (terminal.get().isRemovedHost()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerTerminalContext(player, terminal.get()));
    }

    private static Optional<AiTerminalHost> resolveHostFromBinding(BlockEntity be, TerminalBinding binding) {
        if (be instanceof AiTerminalHost host && binding.side().isEmpty()) {
            return Optional.of(host);
        }

        if (!(be instanceof IPartHost partHost)) {
            return Optional.empty();
        }

        if (binding.side().isEmpty()) {
            return Optional.empty();
        }

        Direction side;
        try {
            side = Direction.valueOf(binding.side().get());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }

        IPart part = partHost.getPart(side);
        if (part instanceof AiTerminalHost host) {
            return Optional.of(host);
        }
        return Optional.empty();
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
