package fr.hardel.leafs.chunk.owner;

/** Game work on a chunk for a caller that only writes: in line on the thread that holds the chunk, mail for its owner otherwise. */
@FunctionalInterface
public interface Router {
    void route(int chunkX, int chunkZ, Runnable task);
}
