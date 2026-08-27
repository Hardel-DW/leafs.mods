package fr.hardel.leafs.chunk;

/** The hold ticket that keeps a chunk, hence its region, alive while mail waits for it. Vanilla dedupes tickets, so the mailbox counts. */
public interface ChunkHoldController {

    void addHold(int chunkX, int chunkZ);

    void removeHold(int chunkX, int chunkZ);
}
