package fr.hardel.leafs.chunk.owner;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkOwnersTest {
    private final ChunkPool pool = new ChunkPool(1, 4);
    private final RegionInbox inbox = new RegionInbox(Long.MAX_VALUE);
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private final List<Runnable> heads = new CopyOnWriteArrayList<>();
    private boolean holding;
    private boolean covered = true;

    private ChunkOwners owners() {
        return new ChunkOwners(pool, 0, (x, z) -> covered ? inbox : null, (x, z) -> holding, (x, z) -> 0, () -> true, Runnable::run, (x, z, task) -> heads.add(task), Long.MAX_VALUE);
    }

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void gameWorkRunsInLineForTheOwner() {
        holding = true;

        assertTrue(owners().submitGame(1, 1, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertTrue(heads.isEmpty());
    }

    @Test
    void gameWorkOnACoveredChunkWaitsForTheRegionTick() {
        assertFalse(owners().submitGame(1, 1, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertTrue(heads.isEmpty());
        assertEquals(1, inbox.drain());
        assertEquals(List.of("later"), ran);
    }

    /** 2026-09-05: a respawn sent to the pool waited for its spawn chunk under the reservation of that same chunk, forever; game work may wait, so it goes to the server thread instead. */
    @Test
    void gameWorkOnAnUncoveredChunkGoesToTheServerThreadNotThePool() {
        covered = false;

        assertFalse(owners().submitGame(1, 1, () -> ran.add("head")));

        assertEquals(List.of(), ran);
        assertEquals(0, pool.queued() + pool.active(), "the pool never runs game work");
        assertEquals(1, heads.size());
        heads.getFirst().run();
        assertEquals(List.of("head"), ran);
    }

    @Test
    void theOwningThreadRunsInLine() {
        holding = true;

        assertTrue(owners().submit(1, 1, () -> ran.add("now")));

        assertEquals(List.of("now"), ran);
        assertEquals(0, inbox.size());
    }

    @Test
    void aCoveredChunkWaitsForTheRegionTick() {
        assertFalse(owners().submit(1, 1, () -> ran.add("later")));

        assertEquals(List.of(), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("later"), ran);
    }

    @Test
    void anUncoveredChunkRunsOnThePool() throws InterruptedException {
        covered = false;
        CountDownLatch done = new CountDownLatch(1);

        assertFalse(owners().submit(1, 1, done::countDown));

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

        owners.submit(1, 1, () -> {
            inLine[0] = owners.holds(1, 1) && owners.submit(1, 1, () -> ran.add("nested"));
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
        owners.submit(1, 1, () -> {
            ran.add("first");
            owners.submit(1, 1, () -> ran.add("second"));
        });

        assertEquals(1, inbox.drain());
        assertEquals(List.of("first"), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second"), ran);
    }

    @Test
    void aDeadRegionHandsItsTasksBack() throws InterruptedException {
        ChunkOwners owners = owners();
        CountDownLatch done = new CountDownLatch(1);
        owners.submit(1, 1, done::countDown);

        covered = false;
        owners.resubmit(inbox);

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertFalse(inbox.post(1, 1, () -> {
        }));
    }
}
