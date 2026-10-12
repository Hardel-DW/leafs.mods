package fr.hardel.excess;

import fr.hardel.TestThreads;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentInt2ObjectMapTest {
    private final ConcurrentInt2ObjectMap<String> map = new ConcurrentInt2ObjectMap<>();

    /** 2026-09-23: LevelChunk calls the Int2ObjectFunction overload, which fell to fastutil's get then put, and two regions built two game event registries for one section. */
    @Test
    void aLookupThroughTheVanillaOverloadBuildsOnce() throws InterruptedException {
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
        AtomicReference<Object> firstBuilt = new AtomicReference<>();
        AtomicReference<Object> secondBuilt = new AtomicReference<>();

        Thread first = Thread.ofPlatform().daemon().start(() -> firstBuilt.set(sections.computeIfAbsent(4, factory)));
        TestThreads.await(building);
        Thread second = Thread.ofPlatform().daemon().start(() -> secondBuilt.set(sections.computeIfAbsent(4, factory)));
        TestThreads.awaitParked(second);
        int buildsWhileTheFirstRuns = builds.get();
        finish.countDown();
        first.join();
        second.join();

        assertEquals(1, buildsWhileTheFirstRuns, "the second lookup waits for the first build");
        assertSame(firstBuilt.get(), secondBuilt.get());
        assertEquals(1, builds.get());
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
