package fr.hardel.excess;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatCollectionsTest {
    private static final int WRITERS = 4;
    private static final int PER_WRITER = 2_000;

    @Test
    void hashSetFacadeSurvivesWritesDuringIteration() throws InterruptedException {
        Set<Integer> set = new HashSetFacade<>(Set.of());
        int walked = walkWhileWriting(() -> set, set::add);
        assertEquals(WRITERS * PER_WRITER, set.size());
        assertTrue(walked > 0);
    }

    @Test
    void hashSetFacadeIteratorRemoveReachesTheLiveSet() {
        Set<Integer> set = new HashSetFacade<>(Set.of());
        set.addAll(List.of(1, 2, 3));
        Iterator<Integer> iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertEquals(2, set.size());
    }

    @Test
    void removableCopyOnWriteListSurvivesWritesDuringIterationAndIteratorRemoves() throws InterruptedException {
        List<Integer> list = new RemovableCopyOnWriteList<>();
        walkWhileWriting(() -> list, list::add);
        assertEquals(WRITERS * PER_WRITER, list.size());

        Iterator<Integer> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() % 2 == 0) {
                iterator.remove();
            }
        }

        assertEquals(WRITERS * PER_WRITER / 2, list.size());
        assertFalse(list.contains(0));
    }

    @Test
    void hashMapFacadePublishesOneValuePerKeyUnderRacingComputeIfAbsent() throws InterruptedException {
        HashMap<Integer, Object> map = new HashMapFacade<>(Map.of());
        List<Object> seen = new CopyOnWriteArrayList<>();
        walkWhileWriting(map::keySet, index -> seen.add(map.computeIfAbsent(index % 8, _ -> new Object())));
        assertEquals(8, map.size());
        for (Object value : seen) {
            assertTrue(map.containsValue(value));
        }
    }

    @Test
    void hashMapFacadeMergePublishesEveryIncrement() throws InterruptedException {
        HashMap<Integer, Integer> map = new HashMapFacade<>(Map.of());
        walkWhileWriting(map::entrySet, index -> map.merge(index % 3, 1, Integer::sum));
        assertEquals(WRITERS * PER_WRITER, map.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void hashMapFacadeViewsWriteBackToTheLiveMap() {
        HashMap<String, Integer> map = new HashMapFacade<>(Map.of());
        map.putAll(Map.of("a", 1, "b", 2, "c", 3));

        Iterator<Map.Entry<String, Integer>> entries = map.entrySet().iterator();
        Map.Entry<String, Integer> first = entries.next();
        first.setValue(10);
        entries.remove();
        assertFalse(map.containsKey(first.getKey()));

        Iterator<Integer> values = map.values().iterator();
        values.next();
        values.remove();
        assertEquals(1, map.size());

        map.keySet().clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void synchronizedLongOpenHashSetKeepsEveryConcurrentAdd() throws InterruptedException {
        SynchronizedLongOpenHashSet set = new SynchronizedLongOpenHashSet();
        runWriters(set::add, () -> { });
        assertEquals(WRITERS * PER_WRITER, set.size());
    }

    private static int walkWhileWriting(Supplier<Iterable<?>> view, Writer writer) throws InterruptedException {
        AtomicInteger seen = new AtomicInteger();
        runWriters(writer, () -> {
            for (Object ignored : view.get()) {
                seen.incrementAndGet();
            }
        });
        return seen.get();
    }

    private static void runWriters(Writer writer, Runnable walk) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(WRITERS);
        List<Thread> threads = new ArrayList<>();
        for (int w = 0; w < WRITERS; w++) {
            int base = w * PER_WRITER;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < PER_WRITER; i++) {
                    writer.write(base + i);
                }

                done.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        while (done.getCount() > 0) {
            walk.run();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        walk.run();
    }

    @FunctionalInterface
    private interface Writer {
        void write(int index);
    }
}
