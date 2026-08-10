package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.Region;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk unload teardown routed to the region that owned the chunk, hold-free: a hold would re-raise
 * the ticket of the very chunk being dropped. The owner is captured at the unload decision, because
 * the decision also removes the chunk from the regionizer, so a later lookup would find nobody. A
 * dead owner or a closed queue makes the offer fail and the caller falls back to the serial path.
 */
public final class RegionUnloads<R extends RegionTaskHost> {
    private static final int DRAIN_BUDGET_PER_TICK = 64;

    private final ConcurrentHashMap<Long, Region<R>> owners = new ConcurrentHashMap<>();

    public void noteOwner(long pos, Region<R> owner) {
        if (owner != null) {
            owners.put(pos, owner);
        }
    }

    public boolean offerToOwner(long pos, int chunkX, int chunkZ, Runnable teardown) {
        return offer(owners.remove(pos), chunkX, chunkZ, teardown);
    }

    /** Same lane for the autosave sweep: the owner snapshots its own chunks, spread by the drain budget. */
    public boolean offer(Region<R> owner, int chunkX, int chunkZ, Runnable task) {
        return owner != null && owner.data().unloadQueues().offer(new QueuedTask(chunkX, chunkZ, task));
    }

    /** The budget bounds a merge wave's backlog to the tick; leftovers run next tick. */
    public int drain(Region<R> region) {
        RegionTaskQueues queues = region.data().unloadQueues();
        int executed = 0;
        while (executed < DRAIN_BUDGET_PER_TICK) {
            QueuedTask task = queues.poll();
            if (task == null) {
                break;
            }

            executed++;
            task.action().run();
        }

        return executed;
    }
}
