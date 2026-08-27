package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredWorkTest {

    private final FakeTransports transports = new FakeTransports();
    private final List<String> ran = new ArrayList<>();

    @Test
    void aThreadAlreadyHoldingTheDestinationRunsInline() {
        transports.owner = true;

        boolean deferred = DeferredWork.owner(DeferReason.PLAYER_PLACEMENT, transports.stats, 0, 0, () -> ran.add("inline")).submit(transports);

        assertFalse(deferred, "nothing was deferred, the caller must not cancel vanilla");
        assertEquals(List.of("inline"), ran);
        assertTrue(transports.ownerQueue.isEmpty());
        assertEquals(0, transports.stats.deferrals(DeferReason.PLAYER_PLACEMENT).perMinute(), "an inline run is not a deferral");
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

    @Test
    void anAbsentRefusalUnderTheBudgetRequeuesForTheNextPass() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.owner(DeferReason.PORTAL, transports.stats, 0, 0, () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not there yet");
            }

            ran.add("landed");
        }).degraded(10).submit(transports);

        transports.drainOwner();
        assertEquals(List.of(), ran);
        assertEquals(1, transports.ownerQueue.size(), "the retry waits for the next pass, never the same drain");

        transports.drainOwner();
        transports.drainOwner();

        assertEquals(List.of("landed"), ran);
        assertEquals(2, transports.stats.retries(DeferReason.PORTAL).perMinute());
    }

    @Test
    void theAttemptPastTheBudgetIsDroppedNeverSyncLoaded() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.owner(DeferReason.PORTAL, transports.stats, 0, 0, () -> {
            attempts.incrementAndGet();
            throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "refused");
        }).degraded(1).submit(transports);

        transports.drainOwner();
        transports.drainOwner();

        assertEquals(1, attempts.get(), "the attempt past the budget never runs");
        assertEquals(1, transports.stats.drops(DeferReason.PORTAL).perMinute());
        assertTrue(transports.ownerQueue.isEmpty());
    }

    /** 2026-08-20: a retry counted as a fresh deferral too, inflating /leafs metrics. */
    @Test
    void aRetriedDeferralCountsOneDeferralAndItsRetries() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.owner(DeferReason.PORTAL, transports.stats, 0, 0, () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not there yet");
            }
        }).degraded(10).submit(transports);

        transports.drainOwner();
        transports.drainOwner();
        transports.drainOwner();

        assertEquals(1, transports.stats.deferrals(DeferReason.PORTAL).perMinute(), "a replay is a retry, never a second deferral");
        assertEquals(2, transports.stats.retries(DeferReason.PORTAL).perMinute());
    }

    /** 2026-08-20: a retry re-queued every window during generation; a refusal with readiness replays once, at delivery. */
    @Test
    void aRefusalCarryingReadinessReplaysOnDeliveryNotEveryPass() {
        CompletableFuture<Void> delivery = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.owner(DeferReason.PORTAL, transports.stats, 0, 0, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "area demanded in one pass", delivery);
            }

            ran.add("landed");
        }).degraded(10).submit(transports);

        transports.drainOwner();
        assertTrue(transports.ownerQueue.isEmpty(), "the retry must wait for the delivery, never poll the next pass");

        delivery.complete(null);
        assertEquals(1, transports.ownerQueue.size());
        transports.drainOwner();
        assertEquals(List.of("landed"), ran);
    }

    @Test
    void aForeignRefusalIsNeverSwallowed() {
        DeferredWork.owner(DeferReason.PORTAL, transports.stats, 0, 0, () -> {
            throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN, "a genuine bug on the owner");
        }).degraded(10).submit(transports);

        assertThrows(OwnershipViolationException.class, transports::drainOwner);
    }
}
