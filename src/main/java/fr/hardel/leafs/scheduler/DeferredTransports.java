package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;

/**
 * The three execution contexts a {@link DeferredWork} can target, plus the "am I already there"
 * probes its inline rule needs. One implementation per level, injected so scheduler/ stays free of
 * ticking/, global/ and chunk/ types.
 */
public interface DeferredTransports {

    void toWindow(DeferReason reason, Runnable task);

    void toSerial(DeferReason reason, Runnable task);

    void toOwner(DeferReason reason, int chunkX, int chunkZ, Runnable task);

    boolean holdsWindow();

    boolean holdsSerial();

    /** The full ownership test: a universal owner owns every position of its level. */
    boolean owns(int chunkX, int chunkZ);

    /**
     * Runs the task reading like a region: present chunks only, a typed refusal instead of a sync
     * load. The engine's retry loop catches the ABSENT refusal this scope produces.
     */
    void runDegraded(Runnable task);

    SharedChunkHolds holds();

    DeferStats stats();
}
