package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.global.GlobalScheduler;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class RoutingNeighborUpdaterTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final RegionInbox inbox = new RegionInbox();
    private boolean holding;
    private final ChunkOwners owners = ChunkFixtures.owners(pool, (x, z) -> inbox, (x, z) -> holding, (x, z, task) -> { task.run(); return true; },
        new GlobalScheduler(Runnable::run), (_, _) -> 0);

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

    @AfterEach
    void stop() {
        pool.shutdown();
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

    /** 2026-08-29: two chunk workers promoting chunks shared the level's collector and corrupted its stack; an owner without a region collects in its own thread's. */
    @Test
    void anOwnerWithoutARegionCollectsInItsOwnThread() throws InterruptedException {
        holding = true;
        List<RecordingUpdater> created = new ArrayList<>();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, () -> {
            RecordingUpdater updater = new RecordingUpdater();
            created.add(updater);
            return updater;
        }, () -> owners);

        callAll(router);
        Thread other = new Thread(() -> callAll(router));
        other.start();
        other.join();

        assertEquals(2, created.size(), "one collector per thread");
        assertEquals(List.of("shape", "simple", "full", "multi"), created.get(0).calls);
        assertEquals(List.of("shape", "simple", "full", "multi"), created.get(1).calls);
    }

    @Test
    void activeContextRoutesEveryEntryPointToItsCollector() {
        holding = true;
        RecordingUpdater fallback = new RecordingUpdater();
        RecordingUpdater regional = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, () -> fallback, () -> owners);
        WorldTickContext context = WorldTickContext.enter(null, null, dataWith(regional));
        try {
            callAll(router);
        } finally {
            context.exit();
        }

        assertEquals(List.of("shape", "simple", "full", "multi"), regional.calls);
        assertTrue(fallback.calls.isEmpty());
    }

    @Test
    void aForeignChunkUpdateIsMailedToItsOwner() {
        RecordingUpdater fallback = new RecordingUpdater();
        RoutingNeighborUpdater router = new RoutingNeighborUpdater(null, () -> fallback, () -> owners);

        callAll(router);
        assertTrue(fallback.calls.isEmpty(), "nothing runs on the thread that does not own the chunk");
        assertEquals(4, inbox.size());

        holding = true;
        assertEquals(4, inbox.drain());
        assertEquals(List.of("shape", "simple", "full", "multi"), fallback.calls);
    }
}
