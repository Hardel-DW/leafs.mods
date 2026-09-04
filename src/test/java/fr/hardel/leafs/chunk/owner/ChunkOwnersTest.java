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
    private final RegionInbox inbox = new RegionInbox();
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private boolean holding;
    private boolean covered = true;

    private ChunkOwners owners() {
        return new ChunkOwners(pool, 0, (x, z) -> covered ? inbox : null, (x, z) -> holding, (x, z) -> 0, () -> true, Runnable::run);
    }

    @AfterEach
    void stop() {
        pool.shutdown();
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
