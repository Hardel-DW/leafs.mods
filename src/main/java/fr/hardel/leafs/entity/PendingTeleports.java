package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.ChunkHoldController;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-flight cross-region teleports. Origin and destination chunks are held from initiation until
 * placement ran, the placement is scheduled onto the destination's owner, and shutdown completes
 * everything still pending — an entity mid-teleport is never lost. Each teleport places exactly once
 * even when shutdown races the scheduled task.
 */
public final class PendingTeleports<E> {

    public interface PlacementSubmitter {
        void submit(int chunkX, int chunkZ, Runnable placement);
    }

    private record Pending<E>(int originX, int originZ, int destinationX, int destinationZ, E payload, Consumer<E> placement) {
    }

    private final ChunkHoldController holds;
    private final PlacementSubmitter submitter;
    private final Map<Long, Pending<E>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public PendingTeleports(ChunkHoldController holds, PlacementSubmitter submitter) {
        this.holds = holds;
        this.submitter = submitter;
    }

    /** Registers and holds BEFORE the caller removes the entity from its origin. */
    public long begin(int originX, int originZ, int destinationX, int destinationZ, E payload, Consumer<E> placement) {
        long id = nextId.getAndIncrement();
        holds.acquire(originX, originZ);
        holds.acquire(destinationX, destinationZ);
        pending.put(id, new Pending<>(originX, originZ, destinationX, destinationZ, payload, placement));
        submitter.submit(destinationX, destinationZ, () -> complete(id));

        return id;
    }

    /** Runs every still-pending placement inline; the shutdown path that guarantees no entity loss. */
    public void completeAll() {
        for (Long id : new ArrayList<>(pending.keySet())) {
            complete(id);
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    private void complete(long id) {
        Pending<E> teleport = pending.remove(id);
        if (teleport == null) {
            return;
        }

        try {
            teleport.placement().accept(teleport.payload());
        } finally {
            holds.release(teleport.originX(), teleport.originZ());
            holds.release(teleport.destinationX(), teleport.destinationZ());
        }
    }
}
