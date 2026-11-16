package dev.pickyobserver;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Same signal behaviour as the vanilla observer (a 1 redstone tick = 2 game tick pulse),
 * but it only reacts to the blocks placed in its filter grid. An empty grid behaves
 * like a normal observer.
 */
public class PickyObserverBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<PickyObserverBlock> CODEC = simpleCodec(PickyObserverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** True while redstone power reaches the block directly (a lever on it, a repeater into it...). */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public PickyObserverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    protected MapCodec<PickyObserverBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LOCKED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // If the face points the wrong way compared to a vanilla observer in your game,
        // remove or add one .getOpposite() here.
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LOCKED, isReceivingSignal(context.getLevel(), context.getClickedPos(), facing));
    }

    /**
     * True when any neighbour feeds redstone power into this block. The side the observer outputs to
     * (its back) only counts real signal sources, never the solid block our own pulse powers, otherwise
     * every pulse would lock the observer.
     */
    public static boolean isReceivingSignal(Level level, BlockPos pos, Direction facing) {
        Direction back = facing.getOpposite();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            int signal = direction == back
                    ? level.getBlockState(neighbor).getSignal(level, neighbor, direction)
                    : level.getSignal(neighbor, direction);
            if (signal > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean locked = isReceivingSignal(level, pos, state.getValue(FACING));
            if (locked != state.getValue(LOCKED)) {
                level.setBlock(pos, state.setValue(LOCKED, locked), 2);
            }
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PickyObserverBlockEntity observer) {
            observer.setLastSeen(level.getBlockState(pos.relative(state.getValue(FACING))));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 2);
        } else {
            level.setBlock(pos, state.setValue(POWERED, true), 2);
            level.scheduleTick(pos, this, 2);
        }
        this.updateNeighborsInFront(level, pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(FACING) == direction
                && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof PickyObserverBlockEntity observer) {
            boolean shouldPulse = observer.onFrontChanged(neighborState);
            if (shouldPulse && !state.getValue(POWERED)) {
                this.startSignal(level, pos);
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private void startSignal(LevelAccessor level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockPos behind = pos.relative(direction.getOpposite());
        level.neighborChanged(behind, this, pos);
        level.updateNeighborsAtExceptFromFacing(behind, this, direction);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(FACING) == direction ? 15 : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock()) && !level.isClientSide()) {
            BlockState updated = state.setValue(LOCKED, isReceivingSignal(level, pos, state.getValue(FACING)));
            boolean unpower = updated.getValue(POWERED) && !level.getBlockTicks().hasScheduledTick(pos, this);
            if (unpower) {
                updated = updated.setValue(POWERED, false);
            }
            if (updated != state) {
                level.setBlock(pos, updated, unpower ? 18 : 2);
                if (unpower) {
                    this.updateNeighborsInFront(level, pos, updated);
                }
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && state.getValue(POWERED) && level.getBlockTicks().hasScheduledTick(pos, this)) {
                this.updateNeighborsInFront(level, pos, state.setValue(POWERED, false));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(LOCKED)) {
            player.displayClientMessage(Component.translatable("message.pickyobserver.locked"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof PickyObserverBlockEntity observer) {
            player.openMenu(observer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PickyObserverBlockEntity(pos, state);
    }
}
