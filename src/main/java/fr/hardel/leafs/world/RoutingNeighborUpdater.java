package fr.hardel.leafs.world;

import fr.hardel.leafs.scheduler.DeferredTransports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** {@code Level.neighborUpdater} swap: an update on a chunk this thread owns joins its collector, one on a chunk it does not goes to the owner's mail, a tick later. */
public final class RoutingNeighborUpdater extends CollectingNeighborUpdater {
    private final ServerLevel level;
    private final CollectingNeighborUpdater fallback;
    private final DeferredTransports transports;

    public RoutingNeighborUpdater(ServerLevel level, CollectingNeighborUpdater fallback, DeferredTransports transports) {
        super(level, 0);
        this.level = level;
        this.fallback = fallback;
        this.transports = transports;
    }

    public static CollectingNeighborUpdater unwrap(CollectingNeighborUpdater updater) {
        return updater instanceof RoutingNeighborUpdater routing ? routing.fallback : updater;
    }

    private void route(BlockPos pos, Consumer<CollectingNeighborUpdater> update) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (transports.owns(chunkX, chunkZ)) {
            RegionWorldData data = WorldTickContext.activeFor(level);
            update.accept(data == null ? fallback : data.neighborUpdater());
            return;
        }

        transports.toOwner(chunkX, chunkZ, () -> update.accept(this));
    }

    @Override
    public void shapeUpdate(@NonNull Direction direction, @NonNull BlockState neighborState, @NonNull BlockPos pos, @NonNull BlockPos neighborPos, int updateFlags, int updateLimit) {
        route(pos, updater -> updater.shapeUpdate(direction, neighborState, pos, neighborPos, updateFlags, updateLimit));
    }

    @Override
    public void neighborChanged(@NonNull BlockPos pos, @NonNull Block block, @Nullable Orientation orientation) {
        route(pos, updater -> updater.neighborChanged(pos, block, orientation));
    }

    @Override
    public void neighborChanged(@NonNull BlockState state, @NonNull BlockPos pos, @NonNull Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        route(pos, updater -> updater.neighborChanged(state, pos, block, orientation, movedByPiston));
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(@NonNull BlockPos pos, @NonNull Block block, @Nullable Direction skipDirection, @Nullable Orientation orientation) {
        route(pos, updater -> updater.updateNeighborsAtExceptFromFacing(pos, block, skipDirection, orientation));
    }

    @Override
    public void setDebugListener(@Nullable Consumer<BlockPos> debugListener) {
        fallback.setDebugListener(debugListener);
    }
}
