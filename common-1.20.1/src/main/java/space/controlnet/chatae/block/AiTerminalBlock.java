package space.controlnet.chatae.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import space.controlnet.chatae.ChatAENetwork;

public final class AiTerminalBlock extends BaseEntityBlock {
    public AiTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new space.controlnet.chatae.blockentity.AiTerminalBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MenuProvider menuProvider)) {
            return InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (menuProvider instanceof ExtendedMenuProvider extendedMenuProvider) {
                MenuRegistry.openExtendedMenu(serverPlayer, extendedMenuProvider);
            } else {
                MenuRegistry.openMenu(serverPlayer, menuProvider);
            }

            ChatAENetwork.sendSessionSnapshot(serverPlayer);
        }

        return InteractionResult.CONSUME;
    }
}
