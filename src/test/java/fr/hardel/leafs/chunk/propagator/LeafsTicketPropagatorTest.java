package fr.hardel.leafs.chunk.propagator;

import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafsTicketPropagatorTest {

    private final CollectingPropagator propagator = new CollectingPropagator();
    private final Oracle oracle = new Oracle();

    /** Accumulates the callback deltas into absolute levels, zero deletes the position. */
    private static final class CollectingPropagator extends LeafsTicketPropagator {
        final Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap();

        @Override
        protected synchronized void onLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
            for (Iterator<Long2ByteMap.Entry> iterator = updates.long2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
                Long2ByteMap.Entry entry = iterator.next();
                if (entry.getByteValue() == 0) {
                    levels.remove(entry.getLongKey());
                } else {
                    levels.put(entry.getLongKey(), entry.getByteValue());
                }
            }
        }
    }

    /** Reference fixed point: level(p) is the best source minus its Chebyshev distance, floored at zero. */
    private static final class Oracle {
        final Map<Long, Integer> sources = new HashMap<>();

        void set(int posX, int posZ, int level) {
            sources.put(key(posX, posZ), level);
        }

        void remove(int posX, int posZ) {
            sources.remove(key(posX, posZ));
        }

        int levelAt(int posX, int posZ) {
            int best = 0;
            for (Map.Entry<Long, Integer> entry : sources.entrySet()) {
                int sourceX = (int) (long) entry.getKey();
                int sourceZ = (int) (entry.getKey() >> 32);
                int distance = Math.max(Math.abs(posX - sourceX), Math.abs(posZ - sourceZ));
                best = Math.max(best, entry.getValue() - distance);
            }
            return best;
        }

        static long key(int posX, int posZ) {
            return (posX & 0xFFFFFFFFL) | ((posZ & 0xFFFFFFFFL) << 32);
        }
    }

    private void set(int posX, int posZ, int level) {
        propagator.setSource(posX, posZ, level);
        oracle.set(posX, posZ, level);
    }

    private void remove(int posX, int posZ) {
        propagator.removeSource(posX, posZ);
        oracle.remove(posX, posZ);
    }

    /** Compares every position of the bounding box around the sources, margin past the largest reach. */
    private void assertMatchesOracle(int fromX, int fromZ, int toX, int toZ) {
        for (int posZ = fromZ - 63; posZ <= toZ + 63; ++posZ) {
            for (int posX = fromX - 63; posX <= toX + 63; ++posX) {
                int expected = oracle.levelAt(posX, posZ);
                int actual = propagator.levels.get(Oracle.key(posX, posZ));
                if (expected != actual) {
                    assertEquals(expected, actual, "level mismatch at [" + posX + ", " + posZ + "]");
                }
            }
        }

        for (long key : propagator.levels.keySet()) {
            int posX = (int) key;
            int posZ = (int) (key >> 32);
            assertEquals(oracle.levelAt(posX, posZ), propagator.levels.get(key), "stray level at [" + posX + ", " + posZ + "]");
        }
    }

    @Test
    void singleSourceAdd() {
        set(100, 100, 5);
        assertTrue(propagator.hasPendingUpdates());
        assertTrue(propagator.performUpdates(null));
        assertFalse(propagator.hasPendingUpdates());
        assertMatchesOracle(100, 100, 100, 100);
    }

    @Test
    void sourceRemove() {
        set(10, 10, 8);
        propagator.performUpdates(null);
        remove(10, 10);
        assertTrue(propagator.performUpdates(null));
        assertTrue(propagator.levels.isEmpty());
    }

    @Test
    void overlappingSources() {
        set(10, 10, 8);
        set(14, 10, 6);
        set(12, 20, 10);
        propagator.performUpdates(null);
        assertMatchesOracle(10, 10, 14, 20);

        remove(12, 20);
        propagator.performUpdates(null);
        assertMatchesOracle(10, 10, 14, 20);
    }

    @Test
    void sourceLevelChangeUp() {
        set(5, 5, 3);
        propagator.performUpdates(null);
        set(5, 5, 10);
        propagator.performUpdates(null);
        assertMatchesOracle(5, 5, 5, 5);
    }

    @Test
    void sourceLevelChangeDown() {
        set(5, 5, 10);
        propagator.performUpdates(null);
        set(5, 5, 3);
        propagator.performUpdates(null);
        assertMatchesOracle(5, 5, 5, 5);
    }

    @Test
    void crossSectionPropagation() {
        // a max level source on a section corner reaches deep into all three neighbour sections
        set(63, 63, LeafsTicketPropagator.MAX_SOURCE_LEVEL);
        propagator.performUpdates(null);
        assertMatchesOracle(63, 63, 63, 63);

        remove(63, 63);
        propagator.performUpdates(null);
        assertTrue(propagator.levels.isEmpty());
    }

    @Test
    void randomizedSequence() {
        Random random = new Random(812752);

        for (int op = 0; op < 500; ++op) {
            int posX = random.nextInt(128);
            int posZ = random.nextInt(128);
            if (random.nextInt(5) < 3) {
                set(posX, posZ, 1 + random.nextInt(LeafsTicketPropagator.MAX_SOURCE_LEVEL));
            } else {
                remove(posX, posZ);
            }

            if (random.nextInt(4) == 0) {
                propagator.performUpdates(null);
            }
            if (op % 100 == 99) {
                propagator.performUpdates(null);
                assertMatchesOracle(0, 0, 127, 127);
            }
        }

        propagator.performUpdates(null);
        assertMatchesOracle(0, 0, 127, 127);
    }

    @Test
    void multithreadedSmoke() throws InterruptedException {
        AreaLock ticketLock = new AreaLock(LeafsTicketPropagator.SECTION_SHIFT);
        int threadCount = 4;
        // territories 8 sections apart, drains of different threads never overlap
        int territoryStride = 8 << LeafsTicketPropagator.SECTION_SHIFT;

        CountDownLatch start = new CountDownLatch(1);
        List<Map<Long, Integer>> finalSources = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threadCount; ++i) {
            int baseX = i * territoryStride;
            Map<Long, Integer> threadSources = new HashMap<>();
            finalSources.add(threadSources);
            Random random = new Random(97231 + i);

            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int op = 0; op < 200; ++op) {
                        int posX = baseX + random.nextInt(128);
                        int posZ = random.nextInt(128);

                        AreaLock.Node node = ticketLock.lock(posX, posZ, 0);
                        try {
                            if (random.nextInt(5) < 3) {
                                int level = 1 + random.nextInt(LeafsTicketPropagator.MAX_SOURCE_LEVEL);
                                propagator.setSource(posX, posZ, level);
                                threadSources.put(Oracle.key(posX, posZ), level);
                            } else {
                                propagator.removeSource(posX, posZ);
                                threadSources.remove(Oracle.key(posX, posZ));
                            }
                        } finally {
                            ticketLock.unlock(node);
                        }

                        if (random.nextInt(8) == 0) {
                            propagator.performUpdates(ticketLock);
                        }
                    }
                } catch (Throwable failure) {
                    synchronized (failures) {
                        failures.add(failure);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join(30_000L);
            assertFalse(thread.isAlive(), "worker did not finish");
        }
        assertTrue(failures.isEmpty(), () -> "worker failed: " + failures.get(0));

        propagator.performUpdates(ticketLock);
        assertFalse(propagator.hasPendingUpdates());

        for (Map<Long, Integer> threadSources : finalSources) {
            for (Map.Entry<Long, Integer> entry : threadSources.entrySet()) {
                oracle.sources.put(entry.getKey(), entry.getValue());
            }
        }
        for (int i = 0; i < threadCount; ++i) {
            assertMatchesOracle(i * territoryStride, 0, i * territoryStride + 127, 127);
        }
    }
}
