package space.controlnet.chatae.common.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import space.controlnet.chatae.common.ChatAERegistries;
import space.controlnet.chatae.common.terminal.AiTerminalHost;

import java.util.Optional;

public final class AiTerminalMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Optional<AiTerminalHost> host;
    private final Optional<Direction> side;
    private final boolean isPart;

    public AiTerminalMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, null, buf.readBlockPos(), buf.readBoolean() ? buf.readEnum(Direction.class) : null);
    }

    public AiTerminalMenu(int containerId, Inventory inventory, AiTerminalHost host, BlockPos pos, Direction side) {
        super(ChatAERegistries.AI_TERMINAL_MENU.get(), containerId);
        this.pos = pos;
        this.side = Optional.ofNullable(side);
        this.host = Optional.ofNullable(host);
        this.isPart = side != null;
    }

    public Optional<AiTerminalHost> getHost() {
        return host;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Optional<Direction> getSide() {
        return side;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host.isPresent()) {
            AiTerminalHost h = host.get();
            if (h.isRemovedHost()) {
                return false;
            }
            BlockPos hostPos = h.getHostPos();
            return player.distanceToSqr(
                    hostPos.getX() + 0.5,
                    hostPos.getY() + 0.5,
                    hostPos.getZ() + 0.5) <= 64.0;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            space.controlnet.chatae.common.ChatAENetwork.onTerminalClosed(serverPlayer);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public boolean isPart() {
        return isPart;
    }
}
