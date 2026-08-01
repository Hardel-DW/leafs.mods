package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;

/**
 * Position-keyed task routing, one instance per level. Queued tasks hold their target chunk until
 * they run; a failing task is a region crash, not a log line.
 */
public final class RegionScheduler<R extends RegionTaskHost> {
    private final Regionizer<R> regionizer;
    private final SharedChunkHolds holds;

    public RegionScheduler(Regionizer<R> regionizer, SharedChunkHolds holds) {
        this.regionizer = regionizer;
        this.holds = holds;
    }

    /** Inline when the calling thread already ticks the owner, queued otherwise. */
    public void run(int chunkX, int chunkZ, Runnable task) {
        if (isOnOwningRegion(chunkX, chunkZ)) {
            task.run();
            return;
        }

        queue(chunkX, chunkZ, task);
    }

    /**
     * A closed queue means a merge or split re-homed the position while we were looking; re-resolving
     * under the regionizer's read lock always observes the newer owner, so the retry terminates.
     */
    public void queue(int chunkX, int chunkZ, Runnable task) {
        holds.acquire(chunkX, chunkZ);
        QueuedTask queuedTask = new QueuedTask(chunkX, chunkZ, task);
        while (true) {
            Region<R> region = regionizer.regionAt(chunkX, chunkZ);
            if (region == null) {
                holds.release(chunkX, chunkZ);
                throw new IllegalStateException("Chunk hold did not materialise a region at [" + chunkX + ", " + chunkZ + "]");
            }

            if (region.data().taskQueues().offer(queuedTask)) {
                return;
            }

            Thread.onSpinWait();
        }
    }

    /**
     * Runs the tasks queued when the drain started. Tasks are popped one by one, so a throwing task —
     * a region crash by policy — leaves the ones behind it queued with their holds intact instead of
     * discarding them.
     */
    public int drain(Region<R> region) {
        RegionTaskQueues queues = region.data().taskQueues();
        int budget = queues.size();
        int executed = 0;
        while (executed < budget) {
            QueuedTask task = queues.poll();
            if (task == null) {
                break;
            }

            executed++;
            try {
                task.action().run();
            } finally {
                holds.release(task.chunkX(), task.chunkZ());
            }
        }

        return executed;
    }

    /**
     * The unsynchronised lookup is safe here: it only runs on a thread that is ticking a region, and a
     * ticking region's sections cannot be re-homed (merges into it are deferred, splits need it idle).
     * Any other answer than "mine" ends in {@link #queue}, which resolves the owner properly.
     */
    private boolean isOnOwningRegion(int chunkX, int chunkZ) {
        if (!(RegionContext.current() instanceof RegionContext.Region context)) {
            return false;
        }

        Region<R> owner = regionizer.regionAtUnsynchronised(chunkX, chunkZ);

        return owner != null && owner.id() == context.regionId();
    }
}
