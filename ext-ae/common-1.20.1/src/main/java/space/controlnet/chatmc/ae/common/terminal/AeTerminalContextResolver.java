package space.controlnet.chatmc.ae.common.terminal;

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
import space.controlnet.chatmc.common.menu.AiTerminalMenu;
import space.controlnet.chatmc.common.terminal.TerminalContextResolver;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.ae.core.terminal.AiTerminalData;
import space.controlnet.chatmc.ae.core.terminal.AeTerminalContext;

import java.util.Optional;

public final class AeTerminalContextResolver implements TerminalContextResolver {
    @Override
    public Optional<space.controlnet.chatmc.core.terminal.TerminalContext> fromPlayer(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        if (!(player.containerMenu instanceof AiTerminalMenu menu)) {
            return Optional.empty();
        }
        Optional<space.controlnet.chatmc.common.terminal.TerminalHost> host = menu.getHost();
        if (host.isEmpty()) {
            return Optional.empty();
        }
        if (!(host.get() instanceof AeTerminalHost terminal)) {
            return Optional.empty();
        }
        if (terminal.isRemovedHost()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerTerminalContext(player, terminal));
    }

    @Override
    public Optional<space.controlnet.chatmc.core.terminal.TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding) {
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

        Optional<AeTerminalHost> terminal = resolveHostFromBinding(be, binding);
        if (terminal.isEmpty()) {
            return Optional.empty();
        }
        if (terminal.get().isRemovedHost()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerTerminalContext(player, terminal.get()));
    }

    private static Optional<AeTerminalHost> resolveHostFromBinding(BlockEntity be, TerminalBinding binding) {
        if (be instanceof AeTerminalHost host && binding.side().isEmpty()) {
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
        if (part instanceof AeTerminalHost host) {
            return Optional.of(host);
        }
        return Optional.empty();
    }

    private static final class PlayerTerminalContext implements AeTerminalContext {
        private final ServerPlayer player;
        private final AeTerminalHost terminal;

        private PlayerTerminalContext(ServerPlayer player, AeTerminalHost terminal) {
            this.player = player;
            this.terminal = terminal;
        }

        @Override
        public AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, String pageToken) {
            return terminal.listItems(query, craftableOnly, limit, pageToken);
        }

        @Override
        public AiTerminalData.AeListResult listCraftables(String query, int limit, String pageToken) {
            return terminal.listCraftables(query, limit, pageToken);
        }

        @Override
        public AiTerminalData.AeCraftSimulation simulateCraft(String itemId, long count) {
            return terminal.simulateCraft(player, itemId, count);
        }

        @Override
        public AiTerminalData.AeCraftRequest requestCraft(String itemId, long count, String cpuName) {
            return terminal.requestCraft(player, itemId, count, cpuName);
        }

        @Override
        public AiTerminalData.AeJobStatus jobStatus(String jobId) {
            return terminal.jobStatus(jobId);
        }

        @Override
        public AiTerminalData.AeJobStatus cancelJob(String jobId) {
            return terminal.cancelJob(jobId);
        }
    }
}
