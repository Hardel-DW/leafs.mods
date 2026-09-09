package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkHolder;

import java.util.Set;

/** {@code ServerChunkCache} mixin */
public interface ChangedChunksAccess {
    Set<ChunkHolder> leafs$changedHolders();
}
