package fr.hardel.leafs.chunk.pool;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-04: a villager read a chunk behind fifteen thousand equally urgent tasks, its region waited four seconds. 2026-09-05: a region waited 54 s for a light task no index knew. */
@ExtendWith(MinecraftBootstrap.class)
class PlacedTasksTest {
    private static final long[] NONE = {};
    private static final int FAR = 10;

    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final Long2IntOpenHashMap distances = new Long2IntOpenHashMap();
    private final ChunkPlacement placement = new ChunkPlacement(pool, 0, place -> distances.getOrDefault(place.chunkKey(), FAR));
    private final List<String> order = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    /** 2026-09-26: a bot spawn put a thousand tasks around the chunks it read at the first priority, and its region waited 300 ms behind them. */
    @Test
    void onlyWhatTheAwaitedChunkNeedsMovesToTheHead() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(4);
        CountDownLatch gate = TestThreads.occupy(pool);

        distances.put(key(100, 100), 5);
        queue("far", 100, 100, ChunkStatus.FEATURES, done);
        queue("biomes it does not need", 8, 4, ChunkStatus.BIOMES, done);
        queue("structure starts it needs", 8, 4, ChunkStatus.STRUCTURE_STARTS, done);
        queue("itself", 3, 4, ChunkStatus.FULL, done);

        placement.expedite(ChunkNeed.of(ChunkPyramid.GENERATION_PYRAMID, 3, 4, ChunkStatus.FULL));
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(Set.of("itself", "structure starts it needs"), Set.copyOf(order.subList(0, 2)));
        assertEquals(List.of("far", "biomes it does not need"), order.subList(2, 4));
    }

    @Test
    void aChunkThatCameCloserToThePlayersMovesUp() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);
        CountDownLatch gate = TestThreads.occupy(pool);

        distances.put(key(100, 100), 5);
        queue("behind", 100, 100, ChunkStatus.FEATURES, done);
        queue("ahead", 3, 4, ChunkStatus.FEATURES, done);

        distances.put(key(3, 4), 1);
        pool.changed(key(3, 4));
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("ahead", "behind"), order);
    }

    private static long key(int chunkX, int chunkZ) {
        return ChunkTask.key(0, chunkX, chunkZ);
    }

    private void queue(String name, int chunkX, int chunkZ, ChunkStatus status, CountDownLatch done) {
        pool.submit(ChunkTask.of(ChunkTask.Kind.STEP, placement.place(chunkX, chunkZ, chunkX, chunkZ, status), NONE, () -> {
            order.add(name);
            done.countDown();
        }));
    }
}
