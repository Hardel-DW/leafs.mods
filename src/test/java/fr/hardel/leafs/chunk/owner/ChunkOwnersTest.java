package fr.hardel.leafs.chunk.owner;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkOwnersTest {
    private final ChunkPool pool = new ChunkPool(1, 4);
    private final GlobalScheduler server = new GlobalScheduler();
    private final RegionInbox inbox = new RegionInbox(Long.MAX_VALUE);
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private final List<String> taken = new CopyOnWriteArrayList<>();
    private boolean holding;
    private boolean covered = true;
    private boolean chunkHeldByAnother;

    /** The fake taker runs the task on the caller like the real one, or refuses when another thread holds the chunk. */
    private ChunkOwners owners() {
        return new ChunkOwners(pool, 0, (x, z) -> covered ? inbox : null, (x, z) -> holding, (x, z) -> 0, () -> true, Runnable::run, this::take, server, Long.MAX_VALUE);
    }

    private boolean take(int chunkX, int chunkZ, Runnable task) {
        if (chunkHeldByAnother) {
            return false;
        }

        taken.add(chunkX + "," + chunkZ);
        task.run();
        return true;
    }

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void gameWorkRunsInLineForTheOwner() {
        holding = true;

        assertTrue(owners().submit(1, 1, Work.GAME, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertTrue(taken.isEmpty());
    }

    /** N01: a respawn refused by the queue of its player called itself back in line until the stack overflowed; it yields to the next pass instead. */
    @Test
    void laterNeverRunsInLineEvenForTheOwner() {
        holding = true;
        ChunkOwners owners = owners();

        owners.later(1, 1, Work.GAME, () -> ran.add("next pass"));

        assertEquals(List.of(), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("next pass"), ran);
    }

    @Test
    void laterOnAnUncoveredChunkGoesThroughTheServerThread() {
        covered = false;
        holding = true;
        ChunkOwners owners = owners();

        owners.later(1, 1, Work.GAME, () -> ran.add("next tick"));

        assertEquals(List.of(), ran);
        assertTrue(server.drain());
        assertEquals(List.of("next tick"), ran);
    }

    @Test
    void gameWorkOnACoveredChunkWaitsForTheRegionTick() {
        assertFalse(owners().submit(1, 1, Work.GAME, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertTrue(taken.isEmpty());
        assertEquals(1, inbox.drain());
        assertEquals(List.of("later"), ran);
    }

    /** 2026-09-06: a gateway placed on the server thread later could not be read back by the feature that placed it, and never got its exit. */
    @Test
    void gameWorkOnAnUncoveredChunkRunsOnTheCallerWhichTakesTheChunk() {
        covered = false;

        assertTrue(owners().submit(1, 1, Work.GAME, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertEquals(List.of("1,1"), taken);
        assertEquals(0, pool.queued() + pool.active(), "the pool never runs game work");
    }

    /** The taker refuses, the chunk is found in the holder's inbox on the next turn. */
    @Test
    void gameWorkOnAChunkAnotherThreadHoldsIsMailForThatThread() {
        chunkHeldByAnother = true;
        ChunkOwners owners = owners();
        RegionInbox held = owners.borrow(1, 1);
        covered = false;

        assertFalse(owners.submit(1, 1, Work.GAME, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertEquals(1, held.size());
        assertTrue(taken.isEmpty());
    }

    /** 2026-09-05: a respawn sent to the pool waited for its spawn chunk under the reservation of that same chunk, forever. */
    @Test
    void gameWorkFromThePoolGoesToTheServerThread() throws InterruptedException {
        covered = false;
        ChunkOwners owners = owners();
        CountDownLatch posted = new CountDownLatch(1);

        owners.submit(1, 1, Work.CHUNK, () -> {
            assertFalse(owners.submit(2, 2, Work.GAME, () -> ran.add("game")));
            posted.countDown();
        });

        assertTrue(posted.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(), ran);
        assertTrue(server.drain());
        assertEquals(List.of("game"), ran);
        assertEquals(List.of("2,2"), taken);
    }

    /** N02: the pool and a taker kept two registries of the same chunk; the pool task now takes the chunk for its duration, so a taker meanwhile finds it held and its work waits for the release. */
    @Test
    void aPoolTaskHoldsItsChunkAgainstATakerUntilItEnds() throws InterruptedException {
        covered = false;
        ChunkOwners owners = owners();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        owners.submit(1, 1, Work.CHUNK, () -> {
            started.countDown();
            await(finish);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertNull(owners.borrow(1, 1), "the chunk is held by the running pool task");
        assertFalse(owners.submit(1, 1, Work.GAME, () -> ran.add("after")), "game work meanwhile is mail for the pool task");
        assertEquals(List.of(), ran);
        finish.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!server.drain()) {
            assertTrue(System.nanoTime() < deadline, "the mail finds its owner again at release: game work from the pool goes to the server thread");
            Thread.onSpinWait();
        }

        assertEquals(List.of("after"), ran);
        assertNotNull(owners.borrow(1, 1), "the chunk is free again");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** B02: a pool task queued before a thread took the chunk would have run beside it; it reads the owner again when it starts. */
    @Test
    void aQueuedPoolTaskFindsTheChunkTakenWhenItStartsAndHandsItOver() throws InterruptedException {
        covered = false;
        ChunkOwners owners = owners();
        CountDownLatch workerBusy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        owners.submit(2, 2, Work.CHUNK, () -> {
            workerBusy.countDown();
            awaitQuietly(release);
        });
        assertTrue(workerBusy.await(5, TimeUnit.SECONDS));
        owners.submit(1, 1, Work.CHUNK, () -> ran.add("chunk work"));
        RegionInbox taken = owners.borrow(1, 1);

        release.countDown();
        for (int attempt = 0; attempt < 500 && taken.size() == 0; attempt++) {
            Thread.sleep(10);
        }

        assertEquals(List.of(), ran, "the pool task never ran beside the taker");
        assertEquals(1, taken.size());
        owners.release(1, 1, taken);
        for (int attempt = 0; attempt < 500 && ran.isEmpty(); attempt++) {
            Thread.sleep(10);
        }

        assertEquals(List.of("chunk work"), ran);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void aChunkIsTakenOnceUntilReleased() {
        ChunkOwners owners = owners();

        RegionInbox first = owners.borrow(1, 1);
        assertNotNull(first);
        assertNull(owners.borrow(1, 1), "another thread finds the chunk held");

        owners.release(1, 1, first);
        assertNotNull(owners.borrow(1, 1));
    }

    @Test
    void theOwningThreadRunsInLine() {
        holding = true;

        assertTrue(owners().submit(1, 1, Work.CHUNK, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertEquals(0, inbox.size());
    }

    @Test
    void aCoveredChunkWaitsForTheRegionTick() {
        assertFalse(owners().submit(1, 1, Work.CHUNK, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("later"), ran);
    }

    @Test
    void anUncoveredChunkRunsOnThePool() throws InterruptedException {
        covered = false;
        CountDownLatch done = new CountDownLatch(1);

        assertFalse(owners().submit(1, 1, Work.CHUNK, done::countDown));

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(0, inbox.size());
    }

    /** 2026-09-04: a status change on an uncovered chunk re-submitted itself to the pool forever, the worker not counting as its owner. */
    @Test
    void thePoolWorkerHoldsTheChunkOfTheTaskItRuns() throws InterruptedException {
        covered = false;
        ChunkOwners owners = owners();
        CountDownLatch done = new CountDownLatch(1);
        boolean[] inLine = new boolean[1];

        owners.submit(1, 1, Work.CHUNK, () -> {
            inLine[0] = owners.holds(1, 1) && owners.submit(1, 1, Work.CHUNK, () -> ran.add("nested"));
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(inLine[0]);
        assertEquals(List.of("nested"), ran);
        assertFalse(owners.holds(1, 1));
    }

    @Test
    void aDrainRunsWhatWasPostedBeforeIt() {
        ChunkOwners owners = owners();
        owners.submit(1, 1, Work.CHUNK, () -> {
            ran.add("first");
            owners.submit(1, 1, Work.CHUNK, () -> ran.add("second"));
        });

        assertEquals(1, inbox.drain());
        assertEquals(List.of("first"), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second"), ran);
    }

    /** 2026-09-06: a teleport left waiting in a dead region went back to the pool as chunk work; the kind travels with the task. */
    @Test
    void aDeadRegionHandsItsTasksBackAsTheWorkTheyAre() throws InterruptedException {
        ChunkOwners owners = owners();
        CountDownLatch chunkWork = new CountDownLatch(1);
        owners.submit(1, 1, Work.CHUNK, chunkWork::countDown);
        owners.submit(1, 1, Work.GAME, () -> ran.add("game"));

        covered = false;
        owners.resubmit(inbox);

        assertTrue(chunkWork.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("game"), ran);
        assertEquals(List.of("1,1"), taken);
        assertFalse(inbox.post(1, 1, Work.CHUNK, () -> ran.add("too late")), "a closed inbox refuses, the caller routes again");
    }
}
