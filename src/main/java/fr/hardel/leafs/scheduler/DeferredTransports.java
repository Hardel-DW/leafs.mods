package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferStats;

// The road to a chunk's owner plus the "am I already there" probe. One implementation per level; the engine owns the counters, a transport only moves the task.
public interface DeferredTransports {

    void toOwner(int chunkX, int chunkZ, Runnable task);

    /** The full ownership test: a universal owner owns every position of its level. */
    boolean owns(int chunkX, int chunkZ);

    /** Runs the task on present chunks only, a typed refusal instead of a sync load; the engine retries on ABSENT. */
    void runDegraded(Runnable task);

    DeferStats stats();
}
