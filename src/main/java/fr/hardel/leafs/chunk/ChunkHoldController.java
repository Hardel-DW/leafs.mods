package fr.hardel.leafs.chunk;

/** The hold ticket that keeps a chunk at a level, hence its region alive, while mail waits for it. Vanilla dedupes tickets, so the mailbox counts. */
public interface ChunkHoldController {

    void addHold(int chunkX, int chunkZ, MailHold.Level level);

    void removeHold(int chunkX, int chunkZ, MailHold.Level level);
}
