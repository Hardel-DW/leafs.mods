package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keys are spread before the backing map and folded back on every read path, so a packed coordinate comes out as it went in. */
class ConcurrentLong2ObjectMapTest {
    private static final long[] KEYS = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, (7L << 32) | 3L, (-12L << 32) | (45L & 0xFFFFFFFFL)};

    private final ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();

    @Test
    void pointOperationsAndDefaultReturnValue() {
        assertNull(map.get(1L));
        assertNull(map.put(1L, "a"));
        assertEquals("a", map.get(1L));
        assertEquals("a", map.put(1L, "b"));
        assertTrue(map.containsKey(1L));
        assertEquals("b", map.remove(1L));
        assertNull(map.remove(1L));
        assertFalse(map.containsKey(1L));
        assertTrue(map.isEmpty());
    }

    @Test
    void keysRoundTripThroughEveryView() {
        for (long key : KEYS) {
            map.put(key, Long.toString(key));
        }

        LongOpenHashSet seen = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<String> entry : map.long2ObjectEntrySet()) {
            assertEquals(Long.toString(entry.getLongKey()), entry.getValue());
            seen.add(entry.getLongKey());
        }

        assertEquals(KEYS.length, seen.size());
        assertEquals(seen, map.keySet());
        for (long key : KEYS) {
            assertTrue(map.containsKey(key));
            assertEquals(Long.toString(key), map.get(key));
        }
    }

    @Test
    void computeSeesTheUnmixedKey() {
        ConcurrentLong2ObjectMap<Long> keys = new ConcurrentLong2ObjectMap<>();
        long key = (5L << 32) | 9L;
        keys.compute(key, (seen, _) -> seen);
        assertEquals(key, keys.get(key));
        keys.compute(key, (_, _) -> null);
        assertNull(keys.get(key));
    }

    @Test
    void computeIfAbsentComputesOncePerKeyThroughBothOverloads() {
        AtomicInteger invocations = new AtomicInteger();
        Long2ObjectFunction<String> factory = key -> {
            invocations.incrementAndGet();
            return "v" + key;
        };

        assertEquals("v7", map.computeIfAbsent(7L, factory));
        assertEquals("v7", map.computeIfAbsent(7L, factory));
        assertEquals(1, invocations.get());
        assertEquals("v3", map.computeIfAbsent(3L, (java.util.function.LongFunction<String>) key -> "v" + key));
        assertEquals("v3", map.get(3L));
    }

    @Test
    void computeIfAbsentIsAtomicUnderRacingThreads() throws InterruptedException {
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }

                map.computeIfAbsent(42L, (Long2ObjectFunction<String>) key -> {
                    invocations.incrementAndGet();
                    return "shared";
                });
            }));
        }

        threads.forEach(Thread::start);
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(1, invocations.get());
        assertEquals("shared", map.get(42L));
    }

    @Test
    void iterationViewsSeeContents() {
        map.put(1L, "a");
        map.put(2L, "b");

        List<String> values = new ArrayList<>();
        map.values().iterator().forEachRemaining(values::add);
        assertEquals(2, values.size());
        assertTrue(values.contains("a") && values.contains("b"));

        List<Long> keys = new ArrayList<>();
        map.keySet().iterator().forEachRemaining((java.util.function.LongConsumer) keys::add);
        assertTrue(keys.contains(1L) && keys.contains(2L));

        assertEquals(2, map.long2ObjectEntrySet().size());
        map.long2ObjectEntrySet().forEach(entry -> assertEquals(map.get(entry.getLongKey()), entry.getValue()));
    }

    @Test
    void iterationSurvivesConcurrentMutation() {
        for (long i = 0; i < 100; i++) {
            map.put(i, "v" + i);
        }

        int seen = 0;
        for (String value : map.values()) {
            assertTrue(value.startsWith("v") || value.startsWith("x"), "value outside the known universe: " + value);
            map.remove(90L - seen);
            map.put(200L + seen, "x" + seen);
            seen++;
        }

        assertTrue(seen > 0);
    }

    @Test
    void setIteratesTheValuesItWasGiven() {
        ConcurrentLongSet set = new ConcurrentLongSet();
        for (long key : KEYS) {
            set.add(key);
        }

        LongOpenHashSet seen = new LongOpenHashSet(set);
        assertEquals(new LongOpenHashSet(KEYS), seen);
    }

    /** A fixed-size stream over a growing map throws once it sees more than it was told. */
    @Test
    void aStreamOverTheValuesSurvivesGrowth() {
        map.put(1, "a");
        map.put(2, "b");

        List<String> seen = map.values().stream().peek(_ -> map.put((map.size() + 10), "z")).toList();

        assertTrue(seen.size() >= 2);
    }
}
