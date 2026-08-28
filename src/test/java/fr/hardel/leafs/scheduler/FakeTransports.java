package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferStats;

import java.util.ArrayList;
import java.util.List;

/** Recording transports: queued tasks pile up and run only when the test drains them. */
final class FakeTransports implements DeferredTransports {

    final List<Runnable> ownerQueue = new ArrayList<>();
    final DeferStats stats = new DeferStats();
    boolean owner;

    @Override
    public void toOwner(int chunkX, int chunkZ, Runnable task) {
        ownerQueue.add(task);
    }

    @Override
    public boolean owns(int chunkX, int chunkZ) {
        return owner;
    }

    @Override
    public DeferStats stats() {
        return stats;
    }

    /** Drains like the owner's pass: only what was queued before the drain started runs. */
    void drainOwner() {
        List<Runnable> batch = List.copyOf(ownerQueue);
        ownerQueue.clear();
        batch.forEach(Runnable::run);
    }
}
