package fr.hardel.excess;

import fr.hardel.TestThreads;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentInt2ObjectMapTest {
    private final ConcurrentInt2ObjectMap<String> map = new ConcurrentInt2ObjectMap<>();

    /** 2026-09-23: LevelChunk calls the Int2ObjectFunction overload, which fell to fastutil's get then put, and two regions built two game event registries for one section. */
    @Test
    void aLookupThroughTheVanillaOverloadBuildsOnce() throws Exception {
        ConcurrentInt2ObjectMap<Object> sections = new ConcurrentInt2ObjectMap<>();
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        Int2ObjectFunction<Object> factory = _ -> {
            if (builds.incrementAndGet() == 1) {
                building.countDown();
                TestThreads.await(finish);
            }

            return new Object();
        };

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            try {
                Future<Object> first = workers.submit(() -> sections.computeIfAbsent(4, factory));
                assertTrue(building.await(5, TimeUnit.SECONDS));
                Future<Object> second = workers.submit(() -> sections.computeIfAbsent(4, factory));
                assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
                finish.countDown();

                assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertEquals(1, builds.get());
            } finally {
                finish.countDown();
            }
        }
    }

    @Test
    void pointOperationsAndDefaultReturnValue() {
        assertNull(map.get(1));
        assertNull(map.put(1, "a"));
        assertEquals("a", map.get(1));
        assertEquals("a", map.put(1, "b"));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue("b"));
        assertEquals(1, map.size());
        assertEquals("b", map.remove(1));
        assertNull(map.remove(1));
        assertFalse(map.containsKey(1));
        assertTrue(map.isEmpty());
    }

    @Test
    void valuesViewIteratesEverythingWithoutOrderGuarantee() {
        for (int i = 0; i < 50; i++) {
            map.put(i, "v%s".formatted(i));
        }

        List<String> values = new ArrayList<>();
        map.values().iterator().forEachRemaining(values::add);
        assertEquals(50, values.size());
        for (int i = 0; i < 50; i++) {
            assertTrue(values.contains("v%s".formatted(i)));
        }
    }

    @Test
    void entrySetReflectsTheMap() {
        map.put(3, "c");

        assertEquals(1, map.int2ObjectEntrySet().size());
        map.int2ObjectEntrySet().forEach(entry -> {
            assertEquals(3, entry.getIntKey());
            assertEquals("c", entry.getValue());
        });
    }
}
