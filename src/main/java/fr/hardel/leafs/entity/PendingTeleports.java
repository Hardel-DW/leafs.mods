package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.SharedChunkHolds;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** The origin chunk is held on ITS OWN level's table, not the destination's, which is what makes cross-dimension correct. */
public final class PendingTeleports<E> {

    public interface PlacementSubmitter {
        void submit(int chunkX, int chunkZ, Runnable placement);
    }

    private record Pending<E>(SharedChunkHolds originHolds, int originX, int originZ, E payload, Consumer<E> placement) {
    }

    private final PlacementSubmitter submitter;
    private final Map<Long, Pending<E>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public PendingTeleports(PlacementSubmitter submitter) {
        this.submitter = submitter;
    }

    /** Registers and holds the origin BEFORE the caller removes the entity from it. */
    public long begin(SharedChunkHolds originHolds, int originX, int originZ, int destinationX, int destinationZ, E payload, Consumer<E> placement) {
        long id = nextId.getAndIncrement();
        originHolds.acquire(originX, originZ);
        pending.put(id, new Pending<>(originHolds, originX, originZ, payload, placement));
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
            teleport.originHolds().release(teleport.originX(), teleport.originZ());
        }
    }
}
