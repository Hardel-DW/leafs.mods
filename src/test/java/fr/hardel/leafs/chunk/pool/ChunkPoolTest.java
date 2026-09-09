package fr.hardel.leafs.chunk.pool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPoolTest {
    private static final long[] NONE = {};
    private ChunkPool pool;

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void runsASubmittedTask() throws InterruptedException {
        pool = new ChunkPool(2, 4);
        CountDownLatch done = new CountDownLatch(1);

        pool.execute(done::countDown);

        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    /** 2026-09-05: the steps of chunks the players had left kept running, a quarter of the pool for nothing. */
    @Test
    void aWithdrawnQueuedTaskIsDroppedWhenReached() throws InterruptedException {
        pool = new ChunkPool(1, 8);
        CountDownLatch gate = occupyTheWorker();
        List<String> ran = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        ChunkTask stale = new ChunkTask(ChunkTask.Kind.STEP, 1, NONE) {
            @Override
            protected CompletableFuture<?> run() {
                ran.add("stale");
                return null;
            }
        };
        pool.submit(stale);
        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 2, NONE, () -> {
            ran.add("live");
            done.countDown();
        }));

        stale.withdraw();
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("live"), ran);
        assertEquals(0, pool.queued());
    }

    /** Roadmap, two reservation spaces: the count of tasks parked behind a reservation comes before any decision. */
    @Test
    void aTaskParkedBehindAReservationIsCounted() throws InterruptedException {
        pool = new ChunkPool(2, 4);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        long[] chunk = {ChunkTask.key(0, 1, 1)};
        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 1, chunk, () -> {
            holding.countDown();
            await(release);
        }));
        assertTrue(holding.await(5, TimeUnit.SECONDS));

        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 1, chunk, done::countDown));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pool.blocks().total() == 0) {
            assertTrue(System.nanoTime() < deadline, "the second task parks behind the first");
            Thread.onSpinWait();
        }

        release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, pool.blocks().total());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void theMostUrgentQueuedTaskRunsFirst() throws InterruptedException {
        pool = new ChunkPool(1, 8);
        CountDownLatch gate = occupyTheWorker();
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(3);
        for (int priority : new int[] {5, 1, 3}) {
            pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, priority, NONE, () -> {
                order.add(priority);
                done.countDown();
            }));
        }

        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(1, 3, 5), order);
    }

    @Test
    void twoTasksOnTheSameChunkNeverOverlap() throws InterruptedException {
        pool = new ChunkPool(4, 4);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(20);
        for (int index = 0; index < 20; index++) {
            pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 0, new long[] {7L}, () -> {
                if (inside.incrementAndGet() > 1) {
                    overlaps.incrementAndGet();
                }

                pause();
                inside.decrementAndGet();
                done.countDown();
            }));
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(0, overlaps.get());
    }

    @Test
    void tasksOnDifferentChunksRunTogether() throws InterruptedException {
        pool = new ChunkPool(2, 4);
        CyclicBarrier both = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        for (long chunk : new long[] {1L, 2L}) {
            pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 0, new long[] {chunk}, () -> {
                meet(both);
                done.countDown();
            }));
        }

        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void aReturnedFutureKeepsTheReservation() throws InterruptedException {
        pool = new ChunkPool(2, 4);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        pool.submit(new ChunkTask(ChunkTask.Kind.STEP, 0, 9L) {
            @Override
            protected CompletableFuture<?> run() {
                first.countDown();
                return pending;
            }
        });
        assertTrue(first.await(5, TimeUnit.SECONDS));

        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 0, new long[] {9L}, second::countDown));

        assertFalse(second.await(200, TimeUnit.MILLISECONDS));
        pending.complete(null);
        assertTrue(second.await(5, TimeUnit.SECONDS));
    }

    @Test
    void aFailingTaskFreesItsChunk() throws InterruptedException {
        pool = new ChunkPool(1, 4);
        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 0, new long[] {4L}, () -> {
            throw new IllegalStateException("boom");
        }));
        CountDownLatch done = new CountDownLatch(1);

        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, 0, new long[] {4L}, done::countDown));

        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void countsWhatWaitsAndWhatRuns() throws InterruptedException {
        pool = new ChunkPool(1, 4);
        CountDownLatch gate = occupyTheWorker();
        pool.execute(() -> {
        });

        assertEquals(1, pool.active());
        assertEquals(1, pool.queued());
        gate.countDown();
        pool.shutdown();
        assertEquals(0, pool.queued());
    }

    /** Holds the single worker, so later submits queue up. */
    private CountDownLatch occupyTheWorker() throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            wait(gate);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        return gate;
    }

    private static void wait(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void meet(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
