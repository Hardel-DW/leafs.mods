package fr.hardel.leafs.chunk.owner;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.global.GlobalScheduler;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class DeferredWorkTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final RegionInbox inbox = new RegionInbox();
    private final DeferStats stats = new DeferStats();
    private final List<String> ran = new ArrayList<>();
    private boolean holding;
    private final ChunkOwners owners = ChunkFixtures.owners(pool, (x, z) -> inbox, (x, z) -> holding, (x, z, work) -> { work.run(); return true; },
        new GlobalScheduler(Runnable::run), (_, _) -> 0);

    private DeferredWork work(DeferReason reason, Runnable task) {
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
