package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;

/** The generation work still in the pool's queue, by chunk: a step under the chunk it writes, a task driver under its centre. */
final class QueuedSteps {
    private final ConcurrentHashMap<Long, ChunkTask> steps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ChunkTask> drivers = new ConcurrentHashMap<>();

    void stepQueued(long chunkKey, ChunkTask task) {
        steps.put(chunkKey, task);
    }

    void stepStarted(long chunkKey) {
        steps.remove(chunkKey);
    }

    void driverQueued(long centerKey, ChunkTask task) {
        drivers.put(centerKey, task);
    }

    void driverStarted(long centerKey) {
        drivers.remove(centerKey);
    }

    String describeAround(int chunkX, int chunkZ) {
        int radius = ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int queuedSteps = 0;
        int queuedDrivers = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                long key = ChunkPos.pack(chunkX + dx, chunkZ + dz);
                queuedSteps += steps.containsKey(key) ? 1 : 0;
                queuedDrivers += drivers.containsKey(key) ? 1 : 0;
            }
        }

        return queuedSteps + " steps and " + queuedDrivers + " drivers queued within " + radius + ", own step " + steps.containsKey(ChunkPos.pack(chunkX, chunkZ)) + ", own driver " + drivers.containsKey(ChunkPos.pack(chunkX, chunkZ));
    }

    /** Everything queued within vanilla's radius of a required chunk moves to the head of the pool. */
    void expedite(ChunkPool pool, int chunkX, int chunkZ) {
        int radius = ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                long key = ChunkPos.pack(chunkX + dx, chunkZ + dz);
                ChunkTask step = steps.get(key);
                if (step != null) {
                    pool.reprioritise(step, ChunkPool.FIRST);
                }

                ChunkTask driver = drivers.get(key);
                if (driver != null) {
                    pool.reprioritise(driver, ChunkPool.FIRST);
                }
            }
        }
    }
}
