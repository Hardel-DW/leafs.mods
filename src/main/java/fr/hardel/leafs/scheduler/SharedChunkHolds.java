package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * The one refcount table over a level's chunk holds. The ticket funnel is thread-safe, so the 0-to-1
 * and 1-to-0 transitions apply their ticket inline from any thread; the propagation reaction still
 * lands on the serial side through the routed listener.
 */
public final class SharedChunkHolds {
    private final ChunkHoldController controller;
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    public SharedChunkHolds(ChunkHoldController controller) {
        this.controller = controller;
    }

    public synchronized void acquire(int chunkX, int chunkZ) {
        if (counts.addTo(CoordinateKey.pack(chunkX, chunkZ), 1) == 0) {
            controller.addHold(chunkX, chunkZ);
        }
    }

    public synchronized void release(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX, chunkZ);
        int count = counts.get(key);
        if (count == 0) {
            throw new IllegalStateException("Chunk hold released more often than acquired at [" + chunkX + ", " + chunkZ + "]");
        }

        if (count == 1) {
            counts.remove(key);
            controller.removeHold(chunkX, chunkZ);

            return;
        }

        counts.put(key, count - 1);
    }

    public synchronized int heldChunks() {
        return counts.size();
    }
}
