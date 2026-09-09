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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkLevelsTest {
    private static final int LEVELS = 46;
    private static final int NONE = LEVELS - 1;
    private final ChunkLevels graph = new ChunkLevels(LEVELS);
    private final Long2IntOpenHashMap reported = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap reportedOld = new Long2IntOpenHashMap();

    private void record(long key, int old, int now) {
        reportedOld.put(key, old);
        reported.put(key, now);
    }

    private int level(int chunkX, int chunkZ) {
        return graph.level(ChunkPos.pack(chunkX, chunkZ));
    }

    @Test
    void aSourceDecaysOneStepPerChunk() {
        graph.setSource(10, 10, 31);

        assertTrue(graph.drain(this::record));

        assertEquals(31, level(10, 10));
        assertEquals(32, level(11, 10));
        assertEquals(33, level(12, 12));
        assertEquals(44, level(23, 10));
        assertEquals(NONE, level(24, 10));
        assertEquals(27 * 27, reported.size());
        assertEquals(NONE, reportedOld.get(ChunkPos.pack(10, 10)));
    }

    @Test
    void removingTheSourceClearsEverything() {
        graph.setSource(10, 10, 31);
        graph.drain(this::record);
        reported.clear();

        graph.setSource(10, 10, NONE);
        assertTrue(graph.drain(this::record));

        assertEquals(27 * 27, reported.size());
        assertTrue(reported.values().intStream().allMatch(level -> level == NONE));
        assertEquals(31, reportedOld.get(ChunkPos.pack(10, 10)));
        assertEquals(NONE, level(10, 10));
        assertEquals(NONE, level(11, 11));
    }

    /** B09: a section stayed forever once its levels were gone, so the graphs grew with every place ever visited. */
    @Test
    void removingTheSourceRetiresItsSections() {
        graph.setSource(63, 63, 31);
        graph.drain(this::record);
        assertEquals(4, graph.sectionCount(), "the wave crossed into the three neighbouring sections");

        graph.setSource(63, 63, NONE);
        graph.drain(this::record);

        assertEquals(0, graph.sectionCount());
        assertEquals(NONE, level(63, 63));
        graph.setSource(63, 63, 31);
        graph.drain(this::record);
        assertEquals(31, level(63, 63), "a retired section is made again by the next source");
    }

    @Test
    void anOverlappingSourceKeepsItsLevelsWhenTheOtherLeaves() {

        graph.setSource(0, 0, 31);
        graph.setSource(4, 0, 31);
        graph.drain(this::record);

        graph.setSource(0, 0, NONE);
        graph.drain(this::record);

        assertEquals(31, level(4, 0));
        assertEquals(35, level(0, 0));
        assertEquals(44, level(-9, 0));
        assertEquals(NONE, level(-10, 0));
    }

    @Test
    void aWeakenedSourceLowersItsWholeArea() {
        graph.setSource(0, 0, 31);
        graph.drain(this::record);

        graph.setSource(0, 0, 33);
        graph.drain(this::record);

        assertEquals(33, level(0, 0));
        assertEquals(34, level(1, 0));
        assertEquals(44, level(11, 0));
        assertEquals(NONE, level(12, 0));
    }

    @Test
    void aStrengthenedSourceRaisesItsWholeArea() {
        graph.setSource(0, 0, 33);
        graph.drain(this::record);

        graph.setSource(0, 0, 31);
        graph.drain(this::record);

        assertEquals(31, level(0, 0));
        assertEquals(44, level(13, 0));
        assertEquals(NONE, level(14, 0));
    }

    @Test
    void aSourceCrossesSectionBorders() {
        graph.setSource(63, 63, 31);
        graph.drain(this::record);

        assertEquals(32, level(64, 64));
        assertEquals(44, level(76, 63));
        assertEquals(44, level(50, 63));
    }

    @Test
    void anUnchangedBatchDrainsNothing() {
        graph.setSource(0, 0, 31);
        graph.drain(this::record);
        reported.clear();

        graph.setSource(0, 0, 31);

        assertFalse(graph.drain(this::record));
        assertTrue(reported.isEmpty());
    }

    @Test
    void concurrentDrainsSettleLikeASerialComputation() throws InterruptedException {
        int threads = 8;
        int span = 300;
        List<long[]> sources = new ArrayList<>();
        Random random = new Random(42);
        for (int index = 0; index < 400; index++) {
            sources.add(new long[] {random.nextInt(span), random.nextInt(span), 25 + random.nextInt(16)});
        }

        CountDownLatch done = new CountDownLatch(threads);
        for (int thread = 0; thread < threads; thread++) {
            int slice = thread;
            new Thread(() -> {
                for (int index = slice; index < sources.size(); index += threads) {
                    long[] source = sources.get(index);
                    graph.setSource((int) source[0], (int) source[1], (int) source[2]);
                    graph.drain((key, old, now) -> {
                    });
                }

                done.countDown();
            }).start();
        }

        assertTrue(done.await(30, TimeUnit.SECONDS));
        graph.drain(this::record);
        for (int chunkX = -50; chunkX < span + 50; chunkX++) {
            for (int chunkZ = -50; chunkZ < span + 50; chunkZ++) {
                int expected = NONE;
                for (long[] source : sources) {
                    int distance = Math.max(Math.abs(chunkX - (int) source[0]), Math.abs(chunkZ - (int) source[1]));
                    expected = Math.min(expected, (int) source[2] + distance);
                }

                assertEquals(expected, level(chunkX, chunkZ), "chunk " + chunkX + "," + chunkZ);
            }
        }
    }

    /** 2026-09-04: a source at the last real level, a ticket at EMPTY, was never raised: the raise stopped one level short. */
    @Test
    void aSourceAtTheLastLevelIsPublished() {
        graph.setSource(5, 5, NONE - 1);

        assertTrue(graph.drain(this::record));

        assertEquals(NONE - 1, level(5, 5));
        assertEquals(NONE, level(6, 5));
    }

    /** 2026-09-04: a thread read the holder without the source it had just posted, its drain having found the queue empty behind another thread's poll. */
    @Test
    void holderWorkUnderTheLocksSeesTheSourcePostedBeforeIt() {
        graph.setSource(10, 10, 31);
        int seen = graph.settled(10, 10, this::record, () -> level(10, 10));
        assertEquals(31, seen);
        assertEquals(32, level(11, 10));
        assertEquals(31, reported.get(ChunkPos.pack(10, 10)));
        assertFalse(graph.drain(this::record));
    }
}
