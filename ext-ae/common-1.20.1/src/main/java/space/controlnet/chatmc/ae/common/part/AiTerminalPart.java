package space.controlnet.chatmc.ae.common.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.ae.core.terminal.AiTerminalData;
import space.controlnet.chatmc.common.menu.AiTerminalMenu;
import space.controlnet.chatmc.ae.common.terminal.AeTerminalHost;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class AiTerminalPart extends AbstractDisplayPart implements AeTerminalHost, ExtendedMenuProvider {
    // Use monitor_base instead of display_base for a solid panel background
    @PartModels
    public static final ResourceLocation MODEL_BASE_MONITOR = new ResourceLocation("ae2", "part/monitor_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = AiTerminalPartModelIds.MODEL_OFF;
    @PartModels
    public static final ResourceLocation MODEL_ON = AiTerminalPartModelIds.MODEL_ON;

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE_MONITOR, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE_MONITOR, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE_MONITOR, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final AiTerminalPartOperations ops = new AiTerminalPartOperations();

    public AiTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
        this.getMainNode()
                .setInWorldNode(true)
                .setTagName("ai_terminal")
                .setVisualRepresentation(ChatMCAePartRegistries.AI_TERMINAL_PART_ITEM.get());
    }

    @Override
    public void setPartHostInfo(Direction side, IPartHost host, BlockEntity blockEntity) {
        super.setPartHostInfo(side, host, blockEntity);
        this.getMainNode().setExposedOnSides(EnumSet.of(side));
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (player.level().isClientSide()) {
            return true;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
             MenuRegistry.openExtendedMenu(serverPlayer, this);
             ChatMCNetwork.onTerminalOpened(serverPlayer);
        }
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.chatmcae.ai_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AiTerminalMenu(containerId, inventory, this, getHostPos(), getSide());
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getHostPos());
        buf.writeBoolean(true);
        buf.writeEnum(getSide());
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, @Nullable String pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }
        return ops.listItems(gridOpt.get(), query, craftableOnly, limit, pageToken);
    }

    @Override
    public AiTerminalData.AeListResult listCraftables(String query, int limit, @Nullable String pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.AeListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }
        return ops.listCraftables(gridOpt.get(), query, limit, pageToken);
    }

    @Override
    public AiTerminalData.AeCraftSimulation simulateCraft(Player player, String itemId, long count) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.AeCraftSimulation("", "error", List.of(), Optional.of("AE2 network not connected"));
        }
        return ops.simulateCraft(player, gridOpt.get(), getHostLevel(), this, itemId, count);
    }

    @Override
    public AiTerminalData.AeCraftRequest requestCraft(Player player, String itemId, long count, @Nullable String cpuName) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.AeCraftRequest("", "error", Optional.of("AE2 network not connected"));
        }
        return ops.requestCraft(player, gridOpt.get(), getHostLevel(), this, itemId, count, cpuName);
    }

    @Override
    public AiTerminalData.AeJobStatus jobStatus(String jobId) {
        return ops.jobStatus(jobId);
    }

    @Override
    public AiTerminalData.AeJobStatus cancelJob(String jobId) {
        return ops.cancelJob(jobId);
    }

    @Override
    public BlockPos getHostPos() {
        var host = getHost();
        return host != null ? host.getBlockEntity().getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public @Nullable Level getHostLevel() {
        var host = getHost();
        if (host == null) {
            return null;
        }
        return host.getBlockEntity().getLevel();
    }

    @Override
    public boolean isRemovedHost() {
        var host = getHost();
        return host == null || host.getPart(getSide()) != this || !host.isInWorld();
    }

    @Override
    public com.google.common.collect.ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ops.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return ops.insertCraftedItems(grid, link, what, amount, mode, this);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        ops.jobStateChange(link);
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public void removeFromWorld() {
        ops.clearJobs();
        super.removeFromWorld();
    }
}
