package fr.hardel.leafs.world;

import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.scheduler.DeferredTransports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingNeighborUpdaterTest {

    private static final class RecordingUpdater extends CollectingNeighborUpdater {
        final List<String> calls = new ArrayList<>();

        RecordingUpdater() {
            super(null, 0);
        }

        @Override
        public void shapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit) {
            calls.add("shape");
        }

        @Override
        public void neighborChanged(BlockPos pos, Block block, Orientation orientation) {
            calls.add("simple");
        }

        @Override
        public void neighborChanged(BlockState state, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
            calls.add("full");
        }

        @Override
        public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, Direction skipDirection, Orientation orientation) {
            calls.add("multi");
        }
    }

    /** Owns everything or nothing; a foreign update piles up until the test drains it as the owner would. */
    private static final class FakeTransports implements DeferredTransports {
        final List<Runnable> mail = new ArrayList<>();
        boolean owner = true;

        @Override
        public void toOwner(int chunkX, int chunkZ, Runnable task) {
            mail.add(task);
        }

        @Override
        public boolean owns(int chunkX, int chunkZ) {
            return owner;
        }

        @Override
        public DeferStats stats() {
            return new DeferStats();
        }
    }

    private static RegionWorldData dataWith(CollectingNeighborUpdater updater) {
        return new RegionWorldData(() -> 0L, RandomSource.create(), updater, new PathTypeCache(), 0L);
    }

    private static void callAll(RoutingNeighborUpdater router) {
        BlockPos pos = new BlockPos(0, 64, 0);
        router.shapeUpdate(Direction.NORTH, null, pos, pos.north(), 0, 0);
        router.neighborChanged(pos, null, null);
        router.neighborChanged(null, pos, null, null, false);
        router.updateNeighborsAtExceptFromFacing(pos, null, null, null);
    }

    @Test
    void offTickCallsKeepTheLevelCollector() {
        RecordingUpdater fallback = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback, new FakeTransports());

        callAll(router);

        assertEquals(List.of("shape", "simple", "full", "multi"), fallback.calls);
    }

    @Test
    void activeContextRoutesEveryEntryPointToItsCollector() {
        RecordingUpdater fallback = new RecordingUpdater();
        RecordingUpdater regional = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback, new FakeTransports());
        WorldTickContext.enter(null, null, dataWith(regional));
        try {
            callAll(router);
        } finally {
            WorldTickContext.exit();
        }

        assertEquals(List.of("shape", "simple", "full", "multi"), regional.calls);
        assertTrue(fallback.calls.isEmpty());
    }

    /** A redstone line crossing a seam: the update on the other side waits in the owner's mail and runs there, a tick later. */
    @Test
    void aForeignChunkUpdateIsMailedToItsOwner() {
        RecordingUpdater fallback = new RecordingUpdater();
        FakeTransports transports = new FakeTransports();
        transports.owner = false;
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback, transports);

        callAll(router);
        assertTrue(fallback.calls.isEmpty(), "nothing runs on the thread that does not own the chunk");
        assertEquals(4, transports.mail.size());

        transports.owner = true;
        transports.mail.forEach(Runnable::run);
        assertEquals(List.of("shape", "simple", "full", "multi"), fallback.calls);
    }

    @Test
    void unwrapReturnsTheWrappedCollector() {
        RecordingUpdater fallback = new RecordingUpdater();

        assertSame(fallback, RoutingNeighborUpdater.unwrap(new RoutingNeighborUpdater(null, fallback, new FakeTransports())));
        assertSame(fallback, RoutingNeighborUpdater.unwrap(fallback));
    }
}
