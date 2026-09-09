package fr.hardel.leafs.chunk.ticket;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-04: a region paid 30 ms of level bookkeeping at every chunk its player crossed. */
@ExtendWith(MinecraftBootstrap.class)
class TicketGraphsTest {
    private final ChunkPool pool = new ChunkPool(1, 46);
    private final TicketGraphs graphs = new TicketGraphs();
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private final CountDownLatch published = new CountDownLatch(1);

    private final LevelListener loading = new LevelListener() {
        @Override
        public void changed(long chunkKey, int oldLevel, int newLevel) {
            threads.add(Thread.currentThread().getName());
        }

        @Override
        public void published() {
            published.countDown();
        }
    };

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void aWriterOffThePoolHandsTheLoadingDrainOver() throws InterruptedException {
        List<String> simulation = new CopyOnWriteArrayList<>();
        graphs.listen(loading, (key, old, now) -> simulation.add(Thread.currentThread().getName()), (key, old, now) -> {}, pool);

        graphs.loadingFeed().update(ChunkPos.pack(0, 0), 44, false);
        graphs.simulationFeed().update(ChunkPos.pack(0, 0), 44, false);
        assertTrue(graphs.drain());
        assertEquals(List.of(Thread.currentThread().getName()), simulation);

        assertTrue(published.await(5, TimeUnit.SECONDS));
        assertEquals(1, threads.size());
        assertTrue(threads.getFirst().startsWith("Leafs Chunk Worker"));
    }

    /** 2026-09-04: a worker applied a region's move half way, the removal without the addition, and the region died and was reborn. */
    @Test
    void aBystanderLeavesTheSimulationMoveToItsWriter() throws InterruptedException {
        List<String> simulation = new CopyOnWriteArrayList<>();
        graphs.listen(loading, (key, old, now) -> simulation.add(Thread.currentThread().getName()), (key, old, now) -> {}, pool);
        graphs.simulationFeed().update(ChunkPos.pack(0, 0), 40, false);
        CountDownLatch drained = new CountDownLatch(1);

        pool.execute(() -> {
            graphs.drain();
            drained.countDown();
        });

        assertTrue(drained.await(5, TimeUnit.SECONDS));
        assertTrue(simulation.isEmpty());
        assertTrue(graphs.drain());
        assertFalse(simulation.isEmpty());
        assertTrue(simulation.stream().allMatch(Thread.currentThread().getName()::equals));
    }

    /** B30: Lithium reads the holder right after runDistanceManagerUpdates without passing through vanilla's caller, so the primitive itself settles what was added. */
    @Test
    void aTicketAddedOffThePoolSettlesOnTheCallerAtRunDistanceManagerUpdates() {
        graphs.listen(loading, (key, old, now) -> {}, (key, old, now) -> {}, pool);

        graphs.loadingFeed().update(ChunkPos.pack(0, 0), 44, true);
        graphs.settleWritten(loading);

        assertEquals(List.of(Thread.currentThread().getName()), threads, "the holder exists before the call returns, on this thread");
        graphs.settleWritten(loading);
        assertEquals(1, threads.size(), "settled once");
    }

    @Test
    void aWorkerDrainsInLine() throws InterruptedException {
        graphs.listen(loading, (key, old, now) -> {}, (key, old, now) -> {}, pool);
        boolean[] changed = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);

        pool.execute(() -> {
            graphs.loadingFeed().update(ChunkPos.pack(0, 0), 44, false);
            changed[0] = graphs.drain();
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(changed[0]);
        assertEquals(0, published.getCount());
    }
}
