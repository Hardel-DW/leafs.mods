package fr.hardel.leafs.scheduler;

/**
 * Keeps a task's target chunk loaded until the task runs. {@code acquire} must guarantee a region
 * covers the position before returning. Refcounted by the scheduler: one pair per chunk, not per task.
 */
public interface ChunkHoldController {

    void acquire(int chunkX, int chunkZ);

    void release(int chunkX, int chunkZ);
}
