package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Position-keyed task routing, one instance per level. Queued tasks hold their target chunk until
 * they run; a failing task is a region crash, not a log line.
 */
public final class RegionScheduler<R extends RegionTaskHost> {
    private final Regionizer<R> regionizer;
    private final ChunkHoldController holds;
    private final ConcurrentHashMap<Long, Integer> holdCounts = new ConcurrentHashMap<>();

    public RegionScheduler(Regionizer<R> regionizer, ChunkHoldController holds) {
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

    public void queue(int chunkX, int chunkZ, Runnable task) {
        acquireHold(chunkX, chunkZ);
        QueuedTask queuedTask = new QueuedTask(chunkX, chunkZ, task);
        while (true) {
            Region<R> region = regionizer.regionAt(chunkX, chunkZ);
            if (region == null) {
                throw new IllegalStateException("Chunk hold did not materialise a region at [" + chunkX + ", " + chunkZ + "]");
            }

            if (region.data().taskQueues().offer(queuedTask)) {
                return;
            }
        }
    }

    public int drain(Region<R> region) {
        List<QueuedTask> tasks = region.data().taskQueues().drainSnapshot();
        for (QueuedTask task : tasks) {
            try {
                task.action().run();
            } finally {
                releaseHold(task.chunkX(), task.chunkZ());
            }
        }

        return tasks.size();
    }

    private boolean isOnOwningRegion(int chunkX, int chunkZ) {
        if (!(RegionContext.current() instanceof RegionContext.Region context)) {
            return false;
        }

        Region<R> owner = regionizer.regionAtUnsynchronised(chunkX, chunkZ);

        return owner != null && owner.id() == context.regionId();
    }

    private void acquireHold(int chunkX, int chunkZ) {
        holdCounts.compute(CoordinateKey.pack(chunkX, chunkZ), (key, count) -> {
            if (count == null) {
                holds.acquire(chunkX, chunkZ);
                return 1;
            }

            return count + 1;
        });
    }

    private void releaseHold(int chunkX, int chunkZ) {
        holdCounts.compute(CoordinateKey.pack(chunkX, chunkZ), (key, count) -> {
            if (count == null) {
                throw new IllegalStateException("Chunk hold released more often than acquired at [" + chunkX + ", " + chunkZ + "]");
            }

            if (count == 1) {
                holds.release(chunkX, chunkZ);
                return null;
            }

            return count - 1;
        });
    }
}
