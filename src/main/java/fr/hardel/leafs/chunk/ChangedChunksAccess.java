package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkHolder;

import java.util.Set;

/** Implemented onto {@code ServerChunkCache} by mixin: the holders that changed outside their region's own walk, for the workers' sweep. */
public interface ChangedChunksAccess {

    Set<ChunkHolder> leafs$changedHolders();
}
