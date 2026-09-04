package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-04: a villager read a chunk behind fifteen thousand equally urgent tasks, its region waited four seconds. */
class QueuedStepsTest {
    private static final long[] NONE = {};

    private final ChunkPool pool = new ChunkPool(1, 46);
    private final QueuedSteps queued = new QueuedSteps();
    private final List<String> order = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void whatARequiredChunkDependsOnMovesToTheHead() throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        pool.execute(() -> {
            started.countDown();
            await(gate);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        queue("far step", 100, 100, 5, done, true);
        queue("near step", 3, 4, 10, done, true);
        queue("near driver", -8, 8, 10, done, false);

        queued.expedite(pool, 0, 0);
        gate.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("near step", "near driver", "far step"), order);
    }

    private void queue(String name, int chunkX, int chunkZ, int priority, CountDownLatch done, boolean step) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkTask task = ChunkTask.of(priority, NONE, () -> {
            order.add(name);
            done.countDown();
        });
        if (step) {
            queued.stepQueued(key, task);
        } else {
            queued.driverQueued(key, task);
        }

        pool.submit(task);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
