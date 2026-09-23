package fr.hardel.leafs.chunk.owner;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class ChunkOwnersTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final GlobalScheduler server = new GlobalScheduler(Runnable::run);
    private final RegionInbox inbox = new RegionInbox(Long.MAX_VALUE);
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private final List<String> taken = new CopyOnWriteArrayList<>();
    private boolean holding;
    private boolean covered = true;
    private boolean chunkHeldByAnother;
    private final ChunkOwners owners = ChunkFixtures.owners(pool, (x, z) -> covered ? inbox : null, (x, z) -> holding, this::take, server);

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

        assertTrue(owners.submit(1, 1, Work.GAME, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertTrue(taken.isEmpty());
    }

    /** N01: a respawn refused by the queue of its player called itself back in line until the stack overflowed; it yields to the next pass instead. */
    @Test
    void laterNeverRunsInLineEvenForTheOwner() {
        holding = true;

        owners.later(1, 1, Work.GAME, () -> ran.add("next pass"));

        assertEquals(List.of(), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("next pass"), ran);
    }

    @Test
    void laterOnAnUncoveredChunkGoesThroughTheServerThread() {
        covered = false;
        holding = true;

        owners.later(1, 1, Work.GAME, () -> ran.add("next tick"));

        assertEquals(List.of(), ran);
        assertTrue(server.drain());
        assertEquals(List.of("next tick"), ran);
    }

    @Test
    void gameWorkOnACoveredChunkWaitsForTheRegionTick() {
        assertFalse(owners.submit(1, 1, Work.GAME, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertTrue(taken.isEmpty());
        assertEquals(1, inbox.drain());
        assertEquals(List.of("later"), ran);
    }

    /** 2026-09-06: a gateway placed on the server thread later could not be read back by the feature that placed it, and never got its exit. */
    @Test
    void gameWorkOnAnUncoveredChunkRunsOnTheCallerWhichTakesTheChunk() {
        covered = false;

        assertTrue(owners.submit(1, 1, Work.GAME, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertEquals(List.of("1,1"), taken);
        assertEquals(0, pool.queued() + pool.active(), "the pool never runs game work");
    }

    @Test
    void gameWorkOnAChunkAnotherThreadHoldsIsMailForThatThread() throws InterruptedException {
        chunkHeldByAnother = true;
        covered = false;
        RegionInbox held = takenOnAnotherThread();

        assertFalse(owners.submit(1, 1, Work.GAME, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertEquals(1, held.size());
        assertTrue(taken.isEmpty());
    }

    private RegionInbox takenOnAnotherThread() throws InterruptedException {
        AtomicReference<RegionInbox> held = new AtomicReference<>();
        Thread holder = new Thread(() -> held.set(owners.borrow(1, 1)), "taker");
        holder.start();
        holder.join();
        return held.get();
    }

    /** 2026-09-05: a respawn sent to the pool waited for its spawn chunk under the reservation of that same chunk, forever. */
    @Test
    void gameWorkFromThePoolGoesToTheServerThread() throws InterruptedException {
        covered = false;
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

    @Test
    void lightReservesInItsOwnSpace() {
        assertEquals(owners.area(ChunkTask.Kind.STEP, 1, 1, 0)[0], owners.area(ChunkTask.Kind.OWNER, 1, 1, 0)[0], "publication and generation write the blocks");
        assertFalse(owners.area(ChunkTask.Kind.STEP, 1, 1, 0)[0] == owners.area(ChunkTask.Kind.LIGHT, 1, 1, 0)[0], "light writes the light arrays");
    }

    /** N02: the pool and a taker kept two registries of the same chunk; the pool task now takes the chunk for its duration, so a taker meanwhile finds it held and its work waits for the release. */
    @Test
    void aPoolTaskHoldsItsChunkAgainstATakerUntilItEnds() throws InterruptedException {
        covered = false;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        owners.submit(1, 1, Work.CHUNK, () -> {
            started.countDown();
            TestThreads.await(finish);
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

    /** B02: a pool task queued before a thread took the chunk would have run beside it; it reads the owner again when it starts. */
    @Test
    void aQueuedPoolTaskFindsTheChunkTakenWhenItStartsAndHandsItOver() throws InterruptedException {
        covered = false;
        CountDownLatch release = TestThreads.occupy(pool);
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

    @Test
    void aChunkATakerHoldsStaysItsOnceARegionCoversIt() throws InterruptedException {
        covered = false;
        holding = true;
        AtomicReference<RegionInbox> taken = new AtomicReference<>();
        CountDownLatch took = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread taker = new Thread(() -> {
            taken.set(owners.borrow(1, 1));
            took.countDown();
            TestThreads.await(release);
            owners.release(1, 1, taken.get());
        });
        taker.start();
        assertTrue(took.await(5, TimeUnit.SECONDS));
        covered = true;

        assertFalse(owners.holds(1, 1), "the region does not hold a chunk another thread took");
        assertFalse(owners.submit(1, 1, Work.GAME, () -> ran.add("mail")));
        assertEquals(1, taken.get().size(), "the mail went to the taker");
        assertEquals(0, inbox.size());

        release.countDown();
        taker.join();
        assertTrue(owners.holds(1, 1), "the region holds the chunk once released");
    }

    @Test
    void aTakenChunkReportsItsTakerAndItsMail() throws InterruptedException {
        covered = false;
        assertNull(owners.describeTaken(1, 1));

        RegionInbox taken = takenOnAnotherThread();
        owners.submit(1, 1, Work.GAME, () -> ran.add("mail"));

        assertEquals("taken by thread 'taker' with 1 queued", owners.describeTaken(1, 1));
        owners.release(1, 1, taken);
        assertNull(owners.describeTaken(1, 1));
    }

    /** 2026-09-04: a status change on an uncovered chunk re-submitted itself to the pool forever, the worker not counting as its owner. */
    @Test
    void thePoolWorkerHoldsTheChunkOfTheTaskItRuns() throws InterruptedException {
        covered = false;
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

    /** 2026-09-06: a teleport left waiting in a dead region went back to the pool as chunk work; the kind travels with the task. 2026-09-14: game work waits for the next pump, a release runs nothing on its thread. */
    @Test
    void aDeadRegionHandsItsTasksBackAsTheWorkTheyAre() throws InterruptedException {
        CountDownLatch chunkWork = new CountDownLatch(1);
        owners.submit(1, 1, Work.CHUNK, chunkWork::countDown);
        owners.submit(1, 1, Work.GAME, () -> ran.add("game"));

        covered = false;
        owners.resubmit(inbox);

        assertTrue(chunkWork.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(), ran, "the game work did not run on the releasing thread");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ran.isEmpty() && System.nanoTime() < deadline) {
            server.drain();
        }

        assertEquals(List.of("game"), ran);
        assertEquals(List.of("1,1"), taken);
        assertFalse(inbox.post(1, 1, Work.CHUNK, () -> ran.add("too late")), "a closed inbox refuses, the caller routes again");
    }
}
