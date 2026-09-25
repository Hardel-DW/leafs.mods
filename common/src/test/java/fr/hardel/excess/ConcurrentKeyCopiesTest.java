package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentKeyCopiesTest {
    private static final int KEYS = 64;

    static Stream<LongSet> longSets() {
        return Stream.of(filled(new ConcurrentLongSet()), filled(new ConcurrentOrderedLongSet(42)));
    }

    static Stream<Collection<?>> keyViews() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        for (long key = 1; key <= KEYS; key++) {
            map.put(key, "v");
        }

        return Stream.of(filled(new ConcurrentLongSet()), map.keySet());
    }

    private static LongSet filled(LongSet set) {
        for (long key = 1; key <= KEYS; key++) {
            set.add(key);
        }

        return set;
    }

    /** 2026-09-24: a removal during toLongArray left trailing zeros, a fake chunk (0, 0). */
    @ParameterizedTest
    @MethodSource("longSets")
    void aCopyHoldsOnlyItsElementsWhileAnotherThreadRemoves(LongSet set) throws InterruptedException {
        AtomicBoolean copying = new AtomicBoolean(true);
        Thread churn = Thread.ofPlatform().daemon().start(() -> {
            while (copying.get()) {
                set.remove(1L);
                set.add(1L);
            }
        });

        int corrupted = 0;
        try {
            for (int round = 0; round < 10_000; round++) {
                if (Stream.of(set.toLongArray(), set.toArray(new long[0]), set.longStream().toArray()).anyMatch(copy -> LongArrayList.wrap(copy).contains(0L))) {
                    corrupted++;
                }
            }
        } finally {
            copying.set(false);
            churn.join();
        }

        assertEquals(0, corrupted, "copies holding a key that was never added");
    }

    @ParameterizedTest
    @MethodSource("keyViews")
    void aStreamSurvivesARemovalDuringItsTraversal(Collection<?> keys) {
        Object[] seen = keys.stream().peek(_ -> keys.clear()).toArray();

        assertTrue(seen.length < KEYS, "the traversal saw %d keys after the clear".formatted(seen.length));
    }
}
