package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentLongSetTest {
    private static final int KEYS = 64;

    private final ConcurrentLongSet set = new ConcurrentLongSet();

    ConcurrentLongSetTest() {
        for (long key = 1; key <= KEYS; key++) {
            set.add(key);
        }
    }

    /** 2026-09-24: a removal during toLongArray left trailing zeros, a fake chunk (0, 0). */
    @Test
    void aCopyHoldsOnlyItsElementsWhileAnotherThreadRemoves() throws InterruptedException {
        AtomicBoolean copying = new AtomicBoolean(true);
        Thread churn = Thread.ofPlatform().daemon().start(() -> {
            while (copying.get()) {
                set.remove(1L);
                set.add(1L);
            }
        });

        int corrupted = 0;
        for (int copy = 0; copy < 10_000; copy++) {
            if (LongArrayList.wrap(set.toLongArray()).contains(0L) || LongArrayList.wrap(set.toArray(new long[0])).contains(0L)) {
                corrupted++;
            }
        }

        copying.set(false);
        churn.join();
        assertEquals(0, corrupted, "copies holding a key that was never added");
    }

    @Test
    void aStreamSurvivesARemovalDuringItsTraversal() {
        long[] seen = set.longStream().peek(_ -> set.clear()).toArray();

        assertTrue(seen.length < KEYS, "the traversal saw %d keys after the clear".formatted(seen.length));
    }
}
