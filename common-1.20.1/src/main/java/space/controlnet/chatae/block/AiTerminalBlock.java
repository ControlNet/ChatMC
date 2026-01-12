package space.controlnet.chatae.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import space.controlnet.chatae.ChatAENetwork;

public final class AiTerminalBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape PANEL_NORTH = Block.box(2, 2, 0, 14, 14, 2);
    private static final VoxelShape PANEL_NORTH_INNER = Block.box(4, 4, 2, 12, 12, 3);
    private static final VoxelShape PANEL_SOUTH = Block.box(2, 2, 14, 14, 14, 16);
    private static final VoxelShape PANEL_SOUTH_INNER = Block.box(4, 4, 13, 12, 12, 14);
    private static final VoxelShape PANEL_WEST = Block.box(0, 2, 2, 2, 14, 14);
    private static final VoxelShape PANEL_WEST_INNER = Block.box(2, 4, 4, 3, 12, 12);
    private static final VoxelShape PANEL_EAST = Block.box(14, 2, 2, 16, 14, 14);
    private static final VoxelShape PANEL_EAST_INNER = Block.box(13, 4, 4, 14, 12, 12);

    private static final VoxelShape SHAPE_NORTH = Shapes.or(PANEL_NORTH, PANEL_NORTH_INNER);
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(PANEL_SOUTH, PANEL_SOUTH_INNER);
    private static final VoxelShape SHAPE_WEST = Shapes.or(PANEL_WEST, PANEL_WEST_INNER);
    private static final VoxelShape SHAPE_EAST = Shapes.or(PANEL_EAST, PANEL_EAST_INNER);

    public AiTerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeFor(state);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
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

    private static VoxelShape shapeFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }
}
