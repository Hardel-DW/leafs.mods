package fr.hardel.leafs.chunk.pool;

@FunctionalInterface
public interface Urgency {
    int of(int chunkX, int chunkZ);
}
