package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadWaitsTest {
    private static final long THRESHOLD = 1_000_000_000L;

    @AfterEach
    void leaveNoWait() {
        ThreadWaits.close(null);
    }

    @Test
    void aWaitInsideAWaitRestoresTheOuterOneWhenItCloses() {
        ThreadWaits.Wait outer = ThreadWaits.open(() -> "a chunk");
        ThreadWaits.Wait inner = ThreadWaits.open(() -> "a region");
        assertEquals("a region", ThreadWaits.describe(Thread.currentThread()));

        ThreadWaits.close(inner);
        assertEquals("a chunk", ThreadWaits.describe(Thread.currentThread()));

        ThreadWaits.close(outer);
        assertNull(ThreadWaits.describe(Thread.currentThread()));
    }

    /** 2026-09-09: the server thread hung two minutes on a region nobody ticked, and the watchdog said nothing because only chunk waits were reported. */
    @Test
    void aWaitPastTheThresholdIsReportedWithItsThreadAndWhatItWaitsFor() {
        Thread current = Thread.currentThread();
        long before = System.nanoTime();
        ThreadWaits.open(() -> "region #7 TICKING");
        long after = System.nanoTime();

        assertFalse(ThreadWaits.stalled(before, THRESHOLD).containsKey(current), "younger than the threshold");
        String stalled = ThreadWaits.stalled(after + 2 * THRESHOLD, THRESHOLD).get(current);
        assertTrue(stalled.startsWith("Wait stalled for "));
        assertTrue(stalled.endsWith("on thread '%s': region #7 TICKING".formatted(current.getName())));
    }
}
