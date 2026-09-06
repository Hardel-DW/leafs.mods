package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredWorkTest {
    private final ChunkPool pool = new ChunkPool(1, 4);
    private final RegionInbox inbox = new RegionInbox(Long.MAX_VALUE);
    private final DeferStats stats = new DeferStats();
    private final List<String> ran = new ArrayList<>();
    private boolean holding;

    /** Every chunk is covered by one region, whose inbox only runs when the test drains it. */
    private DeferredWork work(DeferReason reason, Runnable task) {
        ChunkOwners owners = new ChunkOwners(pool, 0, (x, z) -> inbox, (x, z) -> holding, (x, z) -> 0, () -> true, Runnable::run, (x, z, work) -> work.run(), Long.MAX_VALUE);
        return new DeferredWork(owners, stats, 0, 0, reason, () -> true, task);
    }

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void aThreadAlreadyHoldingTheDestinationRunsInline() {
        holding = true;

        boolean deferred = work(DeferReason.PLAYER_TELEPORT, () -> ran.add("inline")).submit();

        assertFalse(deferred, "nothing was deferred, the caller must not cancel vanilla");
        assertEquals(List.of("inline"), ran);
        assertEquals(0, inbox.size());
        assertEquals(0, stats.deferrals(DeferReason.PLAYER_TELEPORT).perMinute(), "an inline run is not a deferral");
    }

    @Test
    void aDeferredTaskQueuesAndRunsAtTheDestination() {
        boolean deferred = work(DeferReason.TELEPORT, () -> ran.add("owner")).submit();

        assertTrue(deferred);
        assertEquals(List.of(), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("owner"), ran);
        assertEquals(1, stats.deferrals(DeferReason.TELEPORT).perMinute());
    }

    @Test
    void aFailedRevalidationDropsTheWorkWithoutRunningIt() {
        work(DeferReason.RESPAWN, () -> ran.add("never"))
            .validIf(() -> false)
            .submit();

        inbox.drain();

        assertEquals(List.of(), ran);
        assertEquals(1, stats.drops(DeferReason.RESPAWN).perMinute());
    }
}
