package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentOrderedLongSetStressTest {
    private static final int GROUP_SHIFT = 42;
    private static final int WRITERS = 4;
    private static final int READERS = 4;
    private static final int GROUPS_PER_WRITER = 8;
    private static final int KEYS_PER_GROUP = 64;
    private static final int WRITER_ROUNDS = 400;

    private final ConcurrentOrderedLongSet set = new ConcurrentOrderedLongSet(GROUP_SHIFT);

    private static long key(long group, int offset) {
        return group << GROUP_SHIFT | offset;
    }

    private static long groupOf(int writer, int index) {
        return (long) index * WRITERS + writer;
    }

    @Test
    void readersIterateWhileWritersMutate() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean writersDone = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        List<TreeSet<Long>> references = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int w = 0; w < WRITERS; w++) {
            int writer = w;
            TreeSet<Long> reference = new TreeSet<>();
            references.add(reference);
            for (int g = 0; g < GROUPS_PER_WRITER; g++) {
                long group = groupOf(writer, g);
                long base = key(group, 0);
                set.add(base);
                reference.add(base);
            }

            threads.add(new Thread(() -> {
                try {
                    start.await();
                    Random random = new Random(writer);
                    for (int round = 0; round < WRITER_ROUNDS; round++) {
                        long group = groupOf(writer, random.nextInt(GROUPS_PER_WRITER));
                        for (int offset = 1; offset < KEYS_PER_GROUP; offset++) {
                            long value = key(group, offset);
                            if (set.add(value)) {
                                reference.add(value);
                            }
                        }

                        for (int offset = 1; offset < KEYS_PER_GROUP; offset += 1 + random.nextInt(3)) {
                            long value = key(group, offset);
                            if (set.remove(value)) {
                                reference.remove(value);
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "writer-" + writer));
        }

        for (int r = 0; r < READERS; r++) {
            int reader = r;
            threads.add(new Thread(() -> {
                try {
                    start.await();
                    Random random = new Random(1000 + reader);
                    long iterations = 0;
                    while (!writersDone.get() || iterations == 0) {
                        long group = groupOf(random.nextInt(WRITERS), random.nextInt(GROUPS_PER_WRITER));
                        long from = key(group, 0);
                        long to = key(group, KEYS_PER_GROUP - 1) + 1;
                        LongIterator iterator = set.subSet(from, to).iterator();
                        long previous = Long.MIN_VALUE;
                        boolean baseSeen = false;
                        int count = 0;
                        while (iterator.hasNext()) {
                            long value = iterator.nextLong();
                            assertTrue(value > previous, "iteration not strictly ascending");
                            assertTrue(value >= from && value < to, "value outside the queried range");
                            assertTrue((value >> GROUP_SHIFT) == group, "value from a foreign group");
                            baseSeen |= value == from;
                            previous = value;
                            count++;
                        }

                        assertTrue(baseSeen, "never-removed base key missing from a snapshot");
                        assertTrue(count <= KEYS_PER_GROUP, "more values than the group universe");
                        iterations++;
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "reader-" + reader));
        }

        threads.forEach(Thread::start);
        start.countDown();
        for (Thread thread : threads.subList(0, WRITERS)) {
            thread.join(30_000);
            assertTrue(!thread.isAlive(), "writer did not finish in time");
        }

        writersDone.set(true);
        for (Thread thread : threads.subList(WRITERS, threads.size())) {
            thread.join(30_000);
            assertTrue(!thread.isAlive(), "reader did not finish in time");
        }

        assertNull(failure.get(), () -> "concurrent failure: " + failure.get());

        TreeSet<Long> union = new TreeSet<>();
        references.forEach(union::addAll);
        assertEquals(union.size(), set.size(), "lost or phantom updates");
        List<Long> iterated = new ArrayList<>();
        LongIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.nextLong());
        }

        assertEquals(new ArrayList<>(union), iterated, "final contents diverge from the writers' references");
        for (long value : union) {
            assertTrue(set.contains(value));
        }
    }
}
