package fr.hardel.leafs.scheduler;

/**
 * The per-level hold primitive, implemented by chunk/. Once {@code addHold} returns, the chunk must
 * have a holder — and therefore a region — covering it; {@code removeHold} gives that up. Vanilla
 * deduplicates tickets by (type, level), so a chunk carries at most ONE hold: {@link SharedChunkHolds}
 * is what lets several users share it.
 */
public interface ChunkHoldController {

    void addHold(int chunkX, int chunkZ);

    void removeHold(int chunkX, int chunkZ);
}
