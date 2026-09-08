package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
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
import java.util.function.Supplier;

/** {@code Level.neighborUpdater} swap: an update on a chunk this thread owns joins its collector, one on a chunk it does not goes to the owner's mail. A region collects in its world data, any other owner in its thread's. */
public final class RoutingNeighborUpdater extends CollectingNeighborUpdater {
    private final ServerLevel level;
    private final Supplier<ChunkOwners> owners;
    private final ThreadLocal<CollectingNeighborUpdater> threadCollector;

    public RoutingNeighborUpdater(ServerLevel level, Supplier<CollectingNeighborUpdater> collectors, Supplier<ChunkOwners> owners) {
        super(level, 0);
        this.level = level;
        this.owners = owners;
        this.threadCollector = ThreadLocal.withInitial(collectors);
    }

    private void route(BlockPos pos, Consumer<CollectingNeighborUpdater> update) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkOwners resolved = owners.get();
        if (resolved.holds(chunkX, chunkZ)) {
            update.accept(collector());
            return;
        }

        resolved.submit(chunkX, chunkZ, Work.GAME, () -> update.accept(this));
    }

    private CollectingNeighborUpdater collector() {
        RegionWorldData data = WorldTickContext.activeFor(level);
        return data == null ? threadCollector.get() : data.neighborUpdater();
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
        collector().setDebugListener(debugListener);
    }
}
