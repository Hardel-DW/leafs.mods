package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * The one refcount table over a level's chunk holds. Every user of a hold - queued region tasks,
 * in-flight teleports - goes through the same instance, because vanilla keeps a single ticket per
 * (type, level) and would otherwise let one user's release drop another's hold.
 *
 * <p>The monitor covers the count AND the ticket call: the pair must be atomic, or a release
 * dropping to zero could overtake a concurrent acquire and leave a counted chunk with no ticket.
 * Contention is a non-issue - a hold is taken once per queued task, not per block update - and the
 * controller must never call back into the holds, which is what keeps the monitor deadlock-free.
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
