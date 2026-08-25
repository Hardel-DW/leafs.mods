package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
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
import java.util.HashSet;
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

    private static RegionWorldData dataWith(CollectingNeighborUpdater updater) {
        return new RegionWorldData(() -> 0L, () -> 0L, _ -> true, new ObjectLinkedOpenHashSet<>(), RandomSource.create(), updater, new HashSet<>(), new PathTypeCache());
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
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback);

        callAll(router);

        assertEquals(List.of("shape", "simple", "full", "multi"), fallback.calls);
    }

    @Test
    void activeContextRoutesEveryEntryPointToItsCollector() {
        RecordingUpdater fallback = new RecordingUpdater();
        RecordingUpdater regional = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback);
        WorldTickContext.enter(null, dataWith(regional), null);
        try {
            callAll(router);
        } finally {
            WorldTickContext.exit();
        }

        assertEquals(List.of("shape", "simple", "full", "multi"), regional.calls);
        assertTrue(fallback.calls.isEmpty());
    }

    @Test
    void foreignScopeFallsBack() {
        RecordingUpdater fallback = new RecordingUpdater();
        RecordingUpdater regional = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, fallback);
        WorldTickContext.enter(new Object(), dataWith(regional), null);
        try {
            router.neighborChanged(new BlockPos(0, 64, 0), null, null);
        } finally {
            WorldTickContext.exit();
        }

        assertEquals(List.of("simple"), fallback.calls);
        assertTrue(regional.calls.isEmpty());
    }

    @Test
    void unwrapReturnsTheWrappedCollector() {
        RecordingUpdater fallback = new RecordingUpdater();

        assertSame(fallback, RoutingNeighborUpdater.unwrap(new RoutingNeighborUpdater(null, fallback)));
        assertSame(fallback, RoutingNeighborUpdater.unwrap(fallback));
    }
}
