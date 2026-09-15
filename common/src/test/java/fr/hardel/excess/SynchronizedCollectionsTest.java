package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Set<Integer> set = new SynchronizedHashSet<>();
        set.addAll(List.of(1, 2, 3));
        Iterator<Integer> iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertEquals(2, set.size());
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
    void hashMapPublishesOneValuePerKeyUnderRacingComputeIfAbsent() throws InterruptedException {
        HashMap<Integer, Object> map = new SynchronizedHashMap<>();
        List<Object> seen = new SynchronizedArrayList<>();
        walkWhileWriting(map::keySet, index -> seen.add(map.computeIfAbsent(index % 8, _ -> new Object())));
        assertEquals(8, map.size());
        for (Object value : seen) {
            assertTrue(map.containsValue(value));
        }

        assertThrows(UnsupportedOperationException.class, () -> map.keySet().remove(0));
    }

    @Test
    void hashMapMergePublishesEveryIncrement() throws InterruptedException {
        HashMap<Integer, Integer> map = new SynchronizedHashMap<>();
        walkWhileWriting(map::entrySet, index -> map.merge(index % 3, 1, Integer::sum));
        assertEquals(WRITERS * PER_WRITER, map.values().stream().mapToInt(Integer::intValue).sum());
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
    void object2ObjectOpenHashMapPublishesOneValuePerKey() throws InterruptedException {
        SynchronizedObject2ObjectOpenHashMap<Integer, Object> map = new SynchronizedObject2ObjectOpenHashMap<>();
        walkWhileWriting(map::values, index -> map.computeIfAbsent(index % 8, _ -> new Object()));
        assertEquals(8, map.size());
        Object first = map.get(0);
        assertSame(first, map.computeIfAbsent(0, _ -> new Object()));
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
