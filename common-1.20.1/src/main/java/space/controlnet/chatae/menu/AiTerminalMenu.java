package space.controlnet.chatae.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import space.controlnet.chatae.ChatAERegistries;

public final class AiTerminalMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public AiTerminalMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public AiTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ChatAERegistries.AI_TERMINAL_MENU.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ChatAERegistries.AI_TERMINAL_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
