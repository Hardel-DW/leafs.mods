package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Position-keyed task routing, one instance per level. Queued tasks hold their target chunk until
 * they run; a failing task is a region crash, not a log line.
 */
public final class RegionScheduler<R extends RegionTaskHost> {
    private final Regionizer<R> regionizer;
    private final SharedChunkHolds holds;
    private final ConcurrentLinkedQueue<QueuedTask> pending = new ConcurrentLinkedQueue<>();

    public RegionScheduler(Regionizer<R> regionizer, SharedChunkHolds holds) {
        this.regionizer = regionizer;
        this.holds = holds;
    }

    public void run(int chunkX, int chunkZ, Runnable task) {
        if (isOnOwningRegion(chunkX, chunkZ)) {
            task.run();
            return;
        }

        queue(chunkX, chunkZ, task);
    }

    /** No region yet means the hold is still a deferred ticket op; the offer completes after the quiesce applies it. */
    public void queue(int chunkX, int chunkZ, Runnable task) {
        holds.acquire(chunkX, chunkZ);
        QueuedTask queuedTask = new QueuedTask(chunkX, chunkZ, task);
        if (!tryOffer(queuedTask)) {
            pending.add(queuedTask);
        }
    }

    public void completePending() {
        int budget = pending.size();
        QueuedTask task;
        while (budget-- > 0 && (task = pending.poll()) != null) {
            if (!tryOffer(task)) {
                holds.release(task.chunkX(), task.chunkZ());
                throw new IllegalStateException("Chunk hold did not materialise a region at [" + task.chunkX() + ", " + task.chunkZ() + "]");
            }
        }
    }

    /** Tasks are popped one by one: a throw leaves the ones behind it queued with their holds intact. */
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

    /** A closed queue means a merge or split re-homed the position mid-offer; re-resolving observes the newer owner. */
    private boolean tryOffer(QueuedTask task) {
        while (true) {
            Region<R> region = regionizer.regionAt(task.chunkX(), task.chunkZ());
            if (region == null) {
                return false;
            }

            if (region.data().taskQueues().offer(task)) {
                return true;
            }

            Thread.onSpinWait();
        }
    }

    /** Safe unsynchronised lookup: a ticking region's sections cannot be re-homed under it. */
    private boolean isOnOwningRegion(int chunkX, int chunkZ) {
        if (!(RegionContext.current() instanceof RegionContext.Region context)) {
            return false;
        }

        Region<R> owner = regionizer.regionAtUnsynchronised(chunkX, chunkZ);

        return owner != null && owner.id() == context.regionId();
    }
}
