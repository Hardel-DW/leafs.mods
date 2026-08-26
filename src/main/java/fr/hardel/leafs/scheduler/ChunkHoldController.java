package fr.hardel.leafs.scheduler;

/** Per-level hold primitive from chunk/. After addHold the chunk has a holder, hence a region. One hold per chunk (vanilla dedupes tickets), {@link SharedChunkHolds} shares it. */
public interface ChunkHoldController {

    void addHold(int chunkX, int chunkZ);

    void removeHold(int chunkX, int chunkZ);
}
