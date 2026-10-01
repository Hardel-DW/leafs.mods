package fr.hardel.leafs.chunk.ticket;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.view.PlayerView;
import fr.hardel.leafs.ticking.LeafsWatchdog;
import fr.hardel.leafs.ticking.OwnWork;
import fr.hardel.leafs.ticking.RegionTickScheduler;
import fr.hardel.leafs.ticking.TestTickHandle;
import fr.hardel.leafs.ticking.TickEpochs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-04: a region paid 30 ms of level bookkeeping at every chunk its player crossed. */
@ExtendWith(MinecraftBootstrap.class)
class TicketGraphsTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final TickEpochs epochs = new TickEpochs(1, () -> this.graphs.drainAtTickEnd());
    private final TicketGraphs graphs = new TicketGraphs(epochs);
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private final CountDownLatch published = new CountDownLatch(1);

    private final LevelListener loading = new LevelListener() {
        @Override
        public void changed(long chunkKey, int oldLevel, int newLevel) {
            threads.add(Thread.currentThread().getName());
        }

        @Override
        public void published(Runnable pass) {
            published.countDown();
        }
    };

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void aWriteOutsideAnyTickIsDrainedOnThePool() {
        CountDownLatch others = new CountDownLatch(2);
        LevelListener other = (_, _, _) -> {
            threads.add(Thread.currentThread().getName());
            others.countDown();
        };
        graphs.listen(() -> loading, other, other, pool);
        CountDownLatch release = TestThreads.occupy(pool);

        graphs.players().setSource(0, 0, 32);
        graphs.simulation().setSource(0, 0, 44);
        graphs.loading().setSource(0, 0, 44);
        graphs.drain();
        assertTrue(threads.isEmpty());

        release.countDown();
        TestThreads.await(published);
        TestThreads.await(others);
        assertEquals(3, threads.size());
        assertTrue(threads.stream().allMatch(name -> name.startsWith("Leafs Chunk Worker")));
    }

    /** 2026-09-27: the server thread drained the players and simulation writes of every region at each tick. */
    @Test
    void aRegionWorkerDrainsItsWritesWhenItsTickEnds() {
        List<String> drainers = new CopyOnWriteArrayList<>();
        CountDownLatch drained = new CountDownLatch(1);
        graphs.listen(() -> loading, (_, _, _) -> { }, (_, _, _) -> {
            drainers.add(Thread.currentThread().getName());
            drained.countDown();
        }, pool);
        CountDownLatch release = TestThreads.occupy(pool);
        RegionTickScheduler scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), epochs, () -> 50_000_000L, false,
            new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), _ -> { }, _ -> { }), (_, _) -> { });
        scheduler.start();
        TestTickHandle region = new TestTickHandle(1, () -> {
            graphs.players().setSource(0, 0, 32);
            graphs.drain();
        });

        scheduler.schedule(region);
        TestThreads.await(drained);
        region.cancel();
        scheduler.shutdown(false, new OwnWork(() -> false));
        release.countDown();

        assertEquals(1, drainers.size());
        assertTrue(drainers.getFirst().startsWith("Leafs Server Region Worker"));
    }

    /** 2026-09-25: a light task drained the whole loading graph under a ScalableLux monitor, and a region waited 71 ms on it. */
    @Test
    void aWorkerHandsTheLoadingDrainOver() throws InterruptedException {
        graphs.listen(() -> loading, (key, old, now) -> {}, (key, old, now) -> {}, pool);
        long[] publishedInLine = new long[1];
        CountDownLatch done = new CountDownLatch(1);

        pool.execute(() -> {
            graphs.loading().setSource(0, 0, 44);
            graphs.drain();
            publishedInLine[0] = published.getCount();
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, publishedInLine[0]);
        assertTrue(published.await(5, TimeUnit.SECONDS));
    }

    /** 2026-09-27: a bystander drained a player's move between its leave and its enter, and his whole view lost its tickets. */
    @Test
    void aBystanderLeavesThePlayerMoveToItsWriter() throws InterruptedException {
        TicketStorage storage = new TicketStorage();
        PlayerView view = new PlayerView(storage, graphs);
        graphs.listen(() -> loading, (key, old, now) -> {}, view.tickets(), pool);
        view.viewDistance(2);
        view.enter(ChunkPos.pack(63, 0));
        graphs.players().drain(view.tickets());
        List<Long> dropped = new CopyOnWriteArrayList<>();
        storage.setLoadingChunkUpdatedListener((key, level, _) -> {
            if (!ChunkLevel.isLoaded(level)) {
                dropped.add(key);
            }
        });
        CountDownLatch left = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);

        Thread mover = new Thread(() -> {
            epochs.open(1);
            view.leave(ChunkPos.pack(63, 0));
            left.countDown();
            TestThreads.await(drained);
            view.enter(ChunkPos.pack(64, 0));
            entered.countDown();
            TestThreads.await(checked);
            epochs.close(1);
        });
        mover.start();
        TestThreads.await(left);
        graphs.players().drain(view.tickets());
        drained.countDown();
        TestThreads.await(entered);
        assertEquals(List.of(), dropped);

        checked.countDown();
        mover.join();
        assertEquals(IntStream.rangeClosed(-2, 2).mapToObj(chunkZ -> ChunkPos.pack(61, chunkZ)).sorted().toList(), dropped.stream().sorted().toList());
    }
}
