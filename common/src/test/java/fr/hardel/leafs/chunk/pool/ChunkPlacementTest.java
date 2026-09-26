package fr.hardel.leafs.chunk.pool;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class ChunkPlacementTest {
    private final ChunkNeed fullAt34 = ChunkNeed.of(ChunkPyramid.GENERATION_PYRAMID, 3, 4, ChunkStatus.FULL);
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final ChunkPlacement placement = new ChunkPlacement(pool, 0, _ -> 0);
    private final List<String> ran = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void lightReservesInItsOwnSpace() {
        assertEquals(placement.area(ChunkTask.Kind.STEP, 1, 1, 0)[0], placement.area(ChunkTask.Kind.OWNER, 1, 1, 0)[0], "publication and generation write the blocks");
        assertNotEquals(placement.area(ChunkTask.Kind.STEP, 1, 1, 0)[0], placement.area(ChunkTask.Kind.LIGHT, 1, 1, 0)[0], "light writes the light arrays");
    }

    /** 2026-09-26: a region slept in parks of 50 microseconds while the busy pool, in nice 19, kept its chunk queued for seconds. */
    @Test
    void aWaiterRunsTheTaskItNeedsOnItsOwnThread() {
        CountDownLatch release = TestThreads.occupy(pool);
        Thread waiter = Thread.currentThread();
        placement.onPool(ChunkTask.Kind.STEP, ChunkStatus.STRUCTURE_STARTS, 8, 4, 0, () -> ran.add(Thread.currentThread() == waiter ? "waiter" : "pool"));

        assertTrue(placement.help(fullAt34, false));
        assertEquals(List.of("waiter"), ran);
        release.countDown();
    }

    @Test
    void aWaiterRunsNeitherGameWorkNorWhatItDoesNotNeed() {
        CountDownLatch release = TestThreads.occupy(pool);
        placement.onPool(ChunkTask.Kind.OWNER, ChunkStatus.FULL, 3, 4, 0, () -> ran.add("publication"));
        placement.onPool(ChunkTask.Kind.STEP, ChunkStatus.BIOMES, 8, 4, 0, () -> ran.add("biomes of another chunk"));

        assertFalse(placement.help(fullAt34, false));
        assertEquals(List.of(), ran);
        release.countDown();
    }

    @Test
    void aWaiterThatHoldsItsChunkTakesItsPublication() {
        CountDownLatch release = TestThreads.occupy(pool);
        placement.onPool(ChunkTask.Kind.OWNER, ChunkStatus.FULL, 4, 4, 0, () -> ran.add("publication of a neighbour"));
        placement.onPool(ChunkTask.Kind.OWNER, ChunkStatus.FULL, 3, 4, 0, () -> ran.add("publication"));

        assertTrue(placement.help(fullAt34, true));
        assertFalse(placement.help(fullAt34, true));
        assertEquals(List.of("publication"), ran);
        release.countDown();
    }

    @Test
    void aWaiterKeepsThePoolReservations() {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        placement.onPool(ChunkTask.Kind.STEP, ChunkStatus.EMPTY, 3, 4, 0, () -> {
            holding.countDown();
            TestThreads.await(release);
        });
        TestThreads.await(holding);
        placement.onPool(ChunkTask.Kind.STEP, ChunkStatus.EMPTY, 3, 4, 0, () -> {
            ran.add("load");
            done.countDown();
        });

        assertTrue(placement.help(fullAt34, false));
        assertEquals(List.of(), ran);

        release.countDown();
        TestThreads.await(done);
        assertEquals(List.of("load"), ran);
    }
}
