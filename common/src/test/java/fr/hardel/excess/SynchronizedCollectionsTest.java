package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shape of every compat patch: some threads add while one walks and removes, nothing throws and nothing is lost. */
class SynchronizedCollectionsTest {
    private static final int WRITERS = 4;
    private static final int PER_WRITER = 2_000;

    @Test
    void hashSetSurvivesWritesDuringIteration() throws InterruptedException {
        Set<Integer> set = new SynchronizedHashSet<>();
        int walked = walkWhileWriting(() -> set, set::add);
        assertEquals(WRITERS * PER_WRITER, set.size());
        assertTrue(walked > 0);
    }

    @Test
    void hashSetIteratorRemoveReachesTheLiveSet() {
        Set<Integer> set = new SynchronizedHashSet<>(List.of(1, 2, 3));
        Iterator<Integer> iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertEquals(2, set.size());
    }

    @Test
    void objectOpenHashSetSurvivesWritesDuringIteration() throws InterruptedException {
        Set<Integer> set = new SynchronizedObjectOpenHashSet<>();
        walkWhileWriting(() -> set, set::add);
        assertEquals(WRITERS * PER_WRITER, set.size());
        set.removeIf(value -> value % 2 == 0);
        assertEquals(WRITERS * PER_WRITER / 2, set.size());
    }

    @Test
    void arrayListSurvivesWritesDuringIterationAndIteratorRemoves() throws InterruptedException {
        List<Integer> list = new SynchronizedArrayList<>();
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
    void arrayDequeSurvivesWritesDuringIterationAndIteratorRemoves() throws InterruptedException {
        ArrayDeque<Integer> deque = new SynchronizedArrayDeque<>();
        walkWhileWriting(() -> deque, deque::add);
        assertEquals(WRITERS * PER_WRITER, deque.size());

        Iterator<Integer> iterator = deque.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() % 2 == 0) {
                iterator.remove();
            }
        }

        assertEquals(WRITERS * PER_WRITER / 2, deque.size());
        assertEquals(1, deque.peekFirst());
    }

    @Test
    void hashMapPublishesOneValuePerKeyUnderRacingComputeIfAbsent() throws InterruptedException {
        HashMap<Integer, Object> map = new SynchronizedHashMap<>();
        List<Object> seen = new SynchronizedArrayList<>();
        walkWhileWriting(map::keySet, index -> seen.add(map.computeIfAbsent(index % 8, _ -> new Object())));
        assertEquals(8, map.size());
        for (Object value : seen) {
            assertTrue(map.containsValue(value));
        }
    }

    @Test
    void hashMapMergePublishesEveryIncrement() throws InterruptedException {
        HashMap<Integer, Integer> map = new SynchronizedHashMap<>();
        walkWhileWriting(map::entrySet, index -> map.merge(index % 3, 1, Integer::sum));
        assertEquals(WRITERS * PER_WRITER, map.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void hashMapViewsWriteBackToTheLiveMap() {
        HashMap<String, Integer> map = new SynchronizedHashMap<>(Map.of("a", 1, "b", 2, "c", 3));

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
    void weakAndIdentityMapsShareTheSameContract() {
        WeakHashMap<Object, Integer> weak = new SynchronizedWeakHashMap<>();
        Object key = new Object();
        weak.merge(key, 1, Integer::sum);
        weak.merge(key, 1, Integer::sum);
        assertEquals(2, weak.get(key));

        IdentityHashMap<Object, Runnable> identity = new SynchronizedIdentityHashMap<>();
        Runnable task = () -> {};
        identity.putIfAbsent(key, task);
        identity.values().forEach(Runnable::run);
        identity.clear();
        assertTrue(identity.isEmpty());
    }

    @Test
    void longOpenHashSetSurvivesWritesDuringIteration() throws InterruptedException {
        SynchronizedLongOpenHashSet set = new SynchronizedLongOpenHashSet();
        runWriters(set::add, () -> {
            LongIterator iterator = set.iterator();
            while (iterator.hasNext()) {
                iterator.nextLong();
            }
        });
        assertEquals(WRITERS * PER_WRITER, set.size());
    }

    @Test
    void fastutilMapsPublishOneValuePerKeyAndWriteBackThroughEntries() throws InterruptedException {
        SynchronizedObject2ObjectOpenHashMap<Integer, Object> map = new SynchronizedObject2ObjectOpenHashMap<>();
        walkWhileWriting(map::values, index -> map.computeIfAbsent(index % 8, _ -> new Object()));
        assertEquals(8, map.size());
        Object first = map.get(0);
        assertSame(first, map.computeIfAbsent(0, _ -> new Object()));

        Iterator<Object2ObjectMap.Entry<Integer, Object>> entries = map.object2ObjectEntrySet().fastIterator();
        entries.next();
        entries.remove();
        assertEquals(7, map.size());

        SynchronizedReference2ObjectOpenHashMap<Object, Integer> byIdentity = new SynchronizedReference2ObjectOpenHashMap<>();
        Object key = new Object();
        byIdentity.computeIfAbsent(key, _ -> 1);
        assertTrue(byIdentity.keySet().contains(key));
        byIdentity.reference2ObjectEntrySet().clear();
        assertTrue(byIdentity.isEmpty());
    }

    /** Runs the writers and, meanwhile, walks a fresh view until they finish; returns how many elements the walks saw. */
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
