package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
        assertEquals(0, transports.stats.deferrals(DeferReason.COMMAND_BLOCK).total(), "an inline run is not a deferral");
    }

    @Test
    void aDeferredTaskQueuesAndRunsAtTheDestination() {
        boolean deferred = DeferredWork.serial(DeferReason.TELEPORT, transports.stats, () -> ran.add("serial")).submit(transports);

        assertTrue(deferred);
        assertEquals(List.of(), ran);
        transports.serialQueue.getFirst().run();
        assertEquals(List.of("serial"), ran);
        assertEquals(1, transports.stats.deferrals(DeferReason.TELEPORT).total());
    }

    @Test
    void aFailedRevalidationDropsTheWorkWithoutRunningIt() {
        DeferredWork.window(DeferReason.RESPAWN, transports.stats, () -> ran.add("never"))
            .validIf(() -> false)
            .submit(transports);

        transports.drainWindow();

        assertEquals(List.of(), ran);
        assertEquals(1, transports.stats.drops(DeferReason.RESPAWN).total());
    }

    @Test
    void anAbsentRefusalUnderTheBudgetRequeuesForTheNextPass() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not there yet");
            }

            ran.add("landed");
        }).degradedWithSyncNet(10).submit(transports);

        transports.drainWindow();
        assertEquals(List.of(), ran);
        assertEquals(1, transports.windowQueue.size(), "the retry waits for the next window, never the same drain");

        transports.drainWindow();
        transports.drainWindow();

        assertEquals(List.of("landed"), ran);
        assertEquals(2, transports.stats.retries(DeferReason.PORTAL).total());
    }

    @Test
    void theAttemptPastTheBudgetRunsRawAsTheSyncNet() {
        AtomicInteger attempts = new AtomicInteger();
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "first pass refused");
            }

            ran.add("net");
        }).degradedWithSyncNet(1).submit(transports);

        transports.drainWindow();
        transports.drainWindow();

        assertEquals(List.of("net"), ran, "attempt == budget runs raw and must not retry again");
        assertTrue(transports.windowQueue.isEmpty());
    }

    @Test
    void aForeignRefusalIsNeverSwallowed() {
        DeferredWork.window(DeferReason.PORTAL, transports.stats, () -> {
            throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN, "a genuine bug under the window");
        }).degradedWithSyncNet(10).submit(transports);

        assertThrows(OwnershipViolationException.class, transports::drainWindow);
    }
}
