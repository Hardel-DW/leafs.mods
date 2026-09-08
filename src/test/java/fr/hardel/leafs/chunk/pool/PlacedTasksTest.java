package fr.hardel.leafs.chunk.pool;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-04: a villager read a chunk behind fifteen thousand equally urgent tasks, its region waited four seconds. 2026-09-05: a region waited 54 s for a light task no index knew. */
class PlacedTasksTest {
    private static final long[] NONE = {};
    private static final int FAR = 10;

    private final ChunkPool pool = new ChunkPool(1, 46);
    private final Long2IntOpenHashMap distances = new Long2IntOpenHashMap();
    private final Urgency urgency = (chunkX, chunkZ) -> distances.getOrDefault(ChunkPos.pack(chunkX, chunkZ), FAR);
    private final List<String> order = new CopyOnWriteArrayList<>();
    private final CountDownLatch gate = new CountDownLatch(1);

    @AfterEach
    void stop() {
        gate.countDown();
        pool.shutdown();
    }

    @Test
    void whatAThreadWaitsForMovesToTheHead() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(3);
        holdTheOnlyWorker();

        distances.put(ChunkPos.pack(100, 100), 5);
        queue("far", 100, 100, 100, 100, done);
        queue("near", 3, 4, 3, 4, done);
        queue("dependency of near", 40, 40, 3, 4, done);

        pool.expedite(key(3, 4));
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(Set.of("near", "dependency of near"), Set.copyOf(order.subList(0, 2)));
        assertEquals("far", order.get(2));
    }

    @Test
    void aChunkThatCameCloserToThePlayersMovesUp() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);
        holdTheOnlyWorker();

        distances.put(ChunkPos.pack(100, 100), 5);
        queue("behind", 100, 100, 100, 100, done);
        queue("ahead", 3, 4, 3, 4, done);

        distances.put(ChunkPos.pack(3, 4), 1);
        pool.changed(key(3, 4));
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("ahead", "behind"), order);
    }

    private static long key(int chunkX, int chunkZ) {
        return ChunkTask.key(0, chunkX, chunkZ);
    }

    private void holdTheOnlyWorker() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            await(gate);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    private void queue(String name, int chunkX, int chunkZ, int centerX, int centerZ, CountDownLatch done) {
        ChunkTask.Place place = new ChunkTask.Place(key(chunkX, chunkZ), key(centerX, centerZ), urgency);
        pool.submit(ChunkTask.of(place, NONE, () -> {
            order.add(name);
            done.countDown();
        }));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
