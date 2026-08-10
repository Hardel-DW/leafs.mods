package fr.hardel.leafs.scheduler;

/** Implemented by the per-region data composite so the scheduler can reach its queues. */
public interface RegionTaskHost {

    RegionTaskQueues taskQueues();

    /** Hold-free lane for chunk unload teardown, drained with its own budget. */
    RegionTaskQueues unloadQueues();
}
