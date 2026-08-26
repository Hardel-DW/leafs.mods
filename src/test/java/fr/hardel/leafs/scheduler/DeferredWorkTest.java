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
        transports.holdsWindow = true;

        boolean deferred = DeferredWork.window(DeferReason.COMMAND_BLOCK, transports.stats, () -> ran.add("inline")).submit(transports);

        assertFalse(deferred, "nothing was deferred, the caller must not cancel vanilla");
        assertEquals(List.of("inline"), ran);
        assertTrue(transports.windowQueue.isEmpty());
        assertEquals(0, transports.stats.deferrals(DeferReason.COMMAND_BLOCK).perMinute(), "an inline run is not a deferral");
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
        DeferredWork.window(DeferReason.RESPAWN, transports.stats, () -> ran.add("never"))
            .validIf(() -> false)
            .submit(transports);

        transports.drainWindow();

        assertEquals(List.of(), ran);
        assertEquals(1, transports.stats.drops(DeferReason.RESPAWN).perMinute());
    }

    @Test
    void anAbsentRefusalUnderTheBudgetRequeuesForTheNextPass() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not there yet");
            }

            ran.add("landed");
        }).degraded(10).submit(transports);

        transports.drainWindow();
        assertEquals(List.of(), ran);
        assertEquals(1, transports.windowQueue.size(), "the retry waits for the next window, never the same drain");

        transports.drainWindow();
        transports.drainWindow();

        assertEquals(List.of("landed"), ran);
        assertEquals(2, transports.stats.retries(DeferReason.PORTAL).perMinute());
    }

    @Test
    void theAttemptPastTheBudgetIsDroppedNeverSyncLoaded() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            attempts.incrementAndGet();
            throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "refused");
        }).degraded(1).submit(transports);

        transports.drainWindow();
        transports.drainWindow();

        assertEquals(1, attempts.get(), "the attempt past the budget never runs");
        assertEquals(1, transports.stats.drops(DeferReason.PORTAL).perMinute());
        assertTrue(transports.windowQueue.isEmpty());
    }

    /**
     * 2026-08-20: a portal retry used to count once as a retry in the engine AND once as a fresh
     * deferral at the transport, so /leafs metrics showed inflated deferral rates under convergence.
     */
    @Test
    void aRetriedDeferralCountsOneDeferralAndItsRetries() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not there yet");
            }
        }).degraded(10).submit(transports);

        transports.drainWindow();
        transports.drainWindow();
        transports.drainWindow();

        assertEquals(1, transports.stats.deferrals(DeferReason.PORTAL).perMinute(), "a replay is a retry, never a second deferral");
        assertEquals(2, transports.stats.retries(DeferReason.PORTAL).perMinute());
    }

    /**
     * 2026-08-20: a first portal travel demanded hundreds of chunks and the retry re-queued every
     * window, opening the barrier 20 times a second for the whole generation. A refusal that carries
     * the readiness of its demand replays exactly once, when the chunks are delivered.
     */
    @Test
    void aRefusalCarryingReadinessReplaysOnDeliveryNotEveryPass() {
        CompletableFuture<Void> delivery = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "area demanded in one pass", delivery);
            }

            ran.add("landed");
        }).degraded(10).submit(transports);

        transports.drainWindow();
        assertTrue(transports.windowQueue.isEmpty(), "the retry must wait for the delivery, never poll the next pass");

        delivery.complete(null);
        assertEquals(1, transports.windowQueue.size());
        transports.drainWindow();
        assertEquals(List.of("landed"), ran);
    }

    @Test
    void aForeignRefusalIsNeverSwallowed() {
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN, "a genuine bug under the window");
        }).degraded(10).submit(transports);

        assertThrows(OwnershipViolationException.class, transports::drainWindow);
    }
}
