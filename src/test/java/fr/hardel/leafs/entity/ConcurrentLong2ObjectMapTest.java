package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentLong2ObjectMapTest {
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
}
