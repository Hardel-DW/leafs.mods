package fr.hardel.leafs.scheduler;

/** Implemented by the per-region data composite so the scheduler can reach its queues. */
public interface RegionTaskHost {

    RegionTaskQueues taskQueues();
}
