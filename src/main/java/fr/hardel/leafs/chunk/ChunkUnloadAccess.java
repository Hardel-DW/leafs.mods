package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkHolder;

/** Implemented onto {@code ChunkMap} by mixin: the unload decisions and vanilla's private teardown scheduling. */
public interface ChunkUnloadAccess {

    ChunkUnloads leafs$unloads();

    OrphanChunks leafs$orphans();

    void leafs$scheduleUnload(long pos, ChunkHolder holder);
}
