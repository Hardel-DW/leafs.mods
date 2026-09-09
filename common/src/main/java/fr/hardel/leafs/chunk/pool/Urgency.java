package fr.hardel.leafs.chunk.pool;

/** The urgency of a chunk, 0 first: what a thread waits for, then the distance to the nearest player. */
@FunctionalInterface
public interface Urgency {
    int of(int chunkX, int chunkZ);
}
