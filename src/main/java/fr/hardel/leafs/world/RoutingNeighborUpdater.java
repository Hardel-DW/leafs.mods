package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@code Level.neighborUpdater} swap: the collector is a reentrancy stack of the current execution, so it resolves by tick context, never by position. Off-tick callers keep the level instance.
 */
public final class RoutingNeighborUpdater extends CollectingNeighborUpdater {
    private final Level level;
    private final CollectingNeighborUpdater fallback;

    public RoutingNeighborUpdater(Level level, CollectingNeighborUpdater fallback) {
        super(level, 0);
        this.level = level;
        this.fallback = fallback;
    }

    public static CollectingNeighborUpdater unwrap(CollectingNeighborUpdater updater) {
        return updater instanceof RoutingNeighborUpdater routing ? routing.fallback : updater;
    }

    private CollectingNeighborUpdater resolve() {
        RegionWorldData data = WorldTickContext.activeFor(level);
        return data == null ? fallback : data.neighborUpdater();
    }

    @Override
    public void shapeUpdate(@NonNull Direction direction, @NonNull BlockState neighborState, @NonNull BlockPos pos, @NonNull BlockPos neighborPos, int updateFlags, int updateLimit) {
        resolve().shapeUpdate(direction, neighborState, pos, neighborPos, updateFlags, updateLimit);
    }

    @Override
    public void neighborChanged(@NonNull BlockPos pos, @NonNull Block block, @Nullable Orientation orientation) {
        resolve().neighborChanged(pos, block, orientation);
    }

    @Override
    public void neighborChanged(@NonNull BlockState state, @NonNull BlockPos pos, @NonNull Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        resolve().neighborChanged(state, pos, block, orientation, movedByPiston);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(@NonNull BlockPos pos, @NonNull Block block, @Nullable Direction skipDirection, @Nullable Orientation orientation) {
        resolve().updateNeighborsAtExceptFromFacing(pos, block, skipDirection, orientation);
    }

    @Override
    public void setDebugListener(@Nullable Consumer<BlockPos> debugListener) {
        fallback.setDebugListener(debugListener);
    }
}
