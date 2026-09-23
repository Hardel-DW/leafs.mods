package fr.hardel.leafs.chunk.owner;

@FunctionalInterface
public interface Router {
    void route(int chunkX, int chunkZ, Runnable task);
}
