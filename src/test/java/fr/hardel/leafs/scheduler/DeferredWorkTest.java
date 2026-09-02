package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredWorkTest {

    private final FakeTransports transports = new FakeTransports();
    private final List<String> ran = new ArrayList<>();

    @Test
    void aThreadAlreadyHoldingTheDestinationRunsInline() {
        transports.owner = true;

        boolean deferred = DeferredWork.owner(DeferReason.PLAYER_TELEPORT, transports.stats, 0, 0, () -> ran.add("inline")).submit(transports);

        assertFalse(deferred, "nothing was deferred, the caller must not cancel vanilla");
        assertEquals(List.of("inline"), ran);
        assertTrue(transports.ownerQueue.isEmpty());
        assertEquals(0, transports.stats.deferrals(DeferReason.PLAYER_TELEPORT).perMinute(), "an inline run is not a deferral");
    }

    @Test
    void aDeferredTaskQueuesAndRunsAtTheDestination() {
        boolean deferred = DeferredWork.owner(DeferReason.TELEPORT, transports.stats, 0, 0, () -> ran.add("owner")).submit(transports);

        assertTrue(deferred);
        assertEquals(List.of(), ran);
        transports.ownerQueue.getFirst().run();
        assertEquals(List.of("owner"), ran);
        assertEquals(1, transports.stats.deferrals(DeferReason.TELEPORT).perMinute());
    }

    @Test
    void aFailedRevalidationDropsTheWorkWithoutRunningIt() {
        DeferredWork.owner(DeferReason.RESPAWN, transports.stats, 0, 0, () -> ran.add("never"))
            .validIf(() -> false)
            .submit(transports);

        transports.drainOwner();

        assertEquals(List.of(), ran);
        assertEquals(1, transports.stats.drops(DeferReason.RESPAWN).perMinute());
    }
}
