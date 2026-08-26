package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.Region;

/** The hold-free teardown lane of a region; a hold would re-raise the ticket of the very chunk being dropped. */
public final class RegionUnloads<R extends RegionTaskHost> {
    private static final int DRAIN_BUDGET_PER_TICK = 64;

    /** False for a dead owner or a closed queue: the caller falls back to the serial path. */
    public boolean offer(Region<R> owner, int chunkX, int chunkZ, Runnable task) {
        return owner != null && owner.data().unloadQueues().offer(new QueuedTask(chunkX, chunkZ, task));
    }

    /** The budget bounds a merge wave's backlog to the tick; leftovers run next tick. */
    public void drain(Region<R> region) {
        RegionTaskQueues queues = region.data().unloadQueues();
        for (int executed = 0; executed < DRAIN_BUDGET_PER_TICK; executed++) {
            QueuedTask task = queues.poll();
            if (task == null) {
                return;
            }

            task.action().run();
        }
    }
}
