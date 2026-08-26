package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferStats;

import java.util.ArrayList;
import java.util.List;

/** Recording transports: queued tasks pile up per destination and run only when the test drains them. */
final class FakeTransports implements DeferredTransports {

    final List<Runnable> windowQueue = new ArrayList<>();
    final List<Runnable> ownerQueue = new ArrayList<>();
    final DeferStats stats = new DeferStats();
    boolean holdsWindow;
    boolean owner;

    @Override
    public void toWindow(Runnable task) {
        windowQueue.add(task);
    }

    @Override
    public void toOwner(int chunkX, int chunkZ, Runnable task) {
        ownerQueue.add(task);
    }

    @Override
    public boolean holdsWindow() {
        return holdsWindow;
    }

    @Override
    public boolean owns(int chunkX, int chunkZ) {
        return owner;
    }

    @Override
    public void runDegraded(Runnable task) {
        task.run();
    }

    @Override
    public DeferStats stats() {
        return stats;
    }

    /** Drains like the real window: only what was queued before the drain started runs. */
    void drainWindow() {
        List<Runnable> batch = List.copyOf(windowQueue);
        windowQueue.clear();
        batch.forEach(Runnable::run);
    }
}
