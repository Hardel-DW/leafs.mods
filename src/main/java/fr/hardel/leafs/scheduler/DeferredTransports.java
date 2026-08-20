package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferStats;

// The three destinations a DeferredWork can target plus the "am I already there" probes. One implementation per level; the engine owns the counters, a transport only moves the task.
public interface DeferredTransports {

    void toWindow(Runnable task);

    void toSerial(Runnable task);

    void toOwner(int chunkX, int chunkZ, Runnable task);

    boolean holdsWindow();

    boolean holdsSerial();

    /** The full ownership test: a universal owner owns every position of its level. */
    boolean owns(int chunkX, int chunkZ);

    /**
     * Runs the task reading like a region: present chunks only, a typed refusal instead of a sync
     * load. The engine's retry loop catches the ABSENT refusal this scope produces.
     */
    void runDegraded(Runnable task);

    DeferStats stats();
}
