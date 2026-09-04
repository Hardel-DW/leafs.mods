package fr.hardel.leafs.chunk.level;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sources that appear, weaken, strengthen and leave in any order, from several threads, must settle like the brute force and never run away. */
class SourceChurnTest {
    private static final int LEVELS = 46;
    private static final int NONE = LEVELS - 1;
    private static final int SPAN = 120;
    private static final int OPERATIONS = 3_000;

    private final ChunkLevels graph = new ChunkLevels(LEVELS);

    @Test
    void churnFromOneThreadSettlesLikeTheBruteForce() {
        Long2IntOpenHashMap sources = new Long2IntOpenHashMap();
        sources.defaultReturnValue(NONE);
        Random random = new Random(7);
        long start = System.nanoTime();
        for (int operation = 0; operation < OPERATIONS; operation++) {
            int chunkX = random.nextInt(SPAN);
            int chunkZ = random.nextInt(SPAN);
            int level = randomLevel(random);
            sources.put(ChunkPos.pack(chunkX, chunkZ), level);
            graph.setSource(chunkX, chunkZ, level);
            if (operation % 7 == 0) {
                graph.drain((key, old, now) -> {});
            }
        }

        graph.drain((key, old, now) -> {});
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis < 10_000, "the churn took " + millis + " ms");
        for (int chunkX = -50; chunkX < SPAN + 50; chunkX++) {
            for (int chunkZ = -50; chunkZ < SPAN + 50; chunkZ++) {
                assertEquals(expected(sources, chunkX, chunkZ), graph.level(ChunkPos.pack(chunkX, chunkZ)), "chunk " + chunkX + "," + chunkZ);
            }
        }
    }

    /** Threads interleave their writes, so the exact picture is not defined; what must hold is that every level stays in range and the run ends. */
    @Test
    void churnFromEightThreadsEndsWithLevelsInRange() throws InterruptedException {
        int threads = 8;
        Random random = new Random(11);
        long[][] operations = new long[OPERATIONS][];
        for (int operation = 0; operation < OPERATIONS; operation++) {
            operations[operation] = new long[] {random.nextInt(SPAN), random.nextInt(SPAN), randomLevel(random)};
        }

        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int slice = thread;
            new Thread(() -> {
                try {
                    for (int index = slice; index < operations.length; index += threads) {
                        long[] operation = operations[index];
                        graph.setSource((int) operation[0], (int) operation[1], (int) operation[2]);
                        graph.drain((key, old, now) -> {});
                    }
                } catch (Throwable failure) {
                    synchronized (failures) {
                        failures.add(failure);
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(15, TimeUnit.SECONDS), "the churn never finished");
        assertEquals(List.of(), failures);
        graph.drain((key, old, now) -> {});
        for (int chunkX = -50; chunkX < SPAN + 50; chunkX++) {
            for (int chunkZ = -50; chunkZ < SPAN + 50; chunkZ++) {
                int level = graph.level(ChunkPos.pack(chunkX, chunkZ));
                assertTrue(level >= 20 || level == NONE, "chunk " + chunkX + "," + chunkZ + " at " + level);
            }
        }
    }

    private static int randomLevel(Random random) {
        return random.nextInt(4) == 0 ? NONE : 20 + random.nextInt(NONE - 20);
    }

    private static int expected(Long2IntOpenHashMap sources, int chunkX, int chunkZ) {
        int expected = NONE;
        for (Long2IntOpenHashMap.Entry entry : sources.long2IntEntrySet()) {
            if (entry.getIntValue() == NONE) {
                continue;
            }

            int distance = Math.max(Math.abs(chunkX - ChunkPos.getX(entry.getLongKey())), Math.abs(chunkZ - ChunkPos.getZ(entry.getLongKey())));
            expected = Math.min(expected, entry.getIntValue() + distance);
        }

        return expected;
    }
}
