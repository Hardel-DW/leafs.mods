package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * The one refcount table over a level's chunk holds. Vanilla TicketStorage is unsynchronized, so the
 * raw ticket ops run level-serial only: 0-to-1 and 1-to-0 transitions enqueue under this monitor, in
 * transition order, and a level-serial caller drains inline while everyone else waits for the quiesce.
 */
public final class SharedChunkHolds {
    private final ChunkHoldController controller;
    private final BooleanSupplier levelSerialHeld;
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
    private final ArrayDeque<TicketOp> pendingOps = new ArrayDeque<>();

    public SharedChunkHolds(ChunkHoldController controller, BooleanSupplier levelSerialHeld) {
        this.controller = controller;
        this.levelSerialHeld = levelSerialHeld;
    }

    public synchronized void acquire(int chunkX, int chunkZ) {
        if (counts.addTo(CoordinateKey.pack(chunkX, chunkZ), 1) == 0) {
            submit(new TicketOp(chunkX, chunkZ, true));
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
            submit(new TicketOp(chunkX, chunkZ, false));

            return;
        }

        counts.put(key, count - 1);
    }

    /** Runs at the quiesce head, before distance updates and the unload sweep. */
    public synchronized void applyPendingOps() {
        if (!levelSerialHeld.getAsBoolean()) {
            throw new IllegalStateException("Ticket ops applied off the level-serial side");
        }

        TicketOp op;
        while ((op = pendingOps.pollFirst()) != null) {
            if (op.add()) {
                controller.addHold(op.chunkX(), op.chunkZ());
            } else {
                controller.removeHold(op.chunkX(), op.chunkZ());
            }
        }
    }

    public synchronized int heldChunks() {
        return counts.size();
    }

    public synchronized int pendingOpCount() {
        return pendingOps.size();
    }

    private void submit(TicketOp op) {
        pendingOps.addLast(op);
        if (levelSerialHeld.getAsBoolean()) {
            applyPendingOps();
        }
    }

    private record TicketOp(int chunkX, int chunkZ, boolean add) {
    }
}
