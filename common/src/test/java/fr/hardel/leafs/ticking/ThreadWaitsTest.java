package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThreadWaitsTest {

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
        ThreadWaits.open(() -> "region #7 TICKING");
        long now = System.nanoTime();

        assertEquals(Map.of(), ThreadWaits.stalled(now, 1_000_000_000L), "younger than the threshold");
        Map<Thread, String> stalled = ThreadWaits.stalled(now + 2_000_000_000L, 1_000_000_000L);
        assertEquals(1, stalled.size());
        assertEquals("Wait stalled for 2s on thread '" + Thread.currentThread().getName() + "': region #7 TICKING", stalled.get(Thread.currentThread()));
    }
}
