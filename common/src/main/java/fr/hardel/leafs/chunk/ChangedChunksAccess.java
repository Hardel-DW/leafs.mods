package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkHolder;

import java.util.Set;

public interface ChangedChunksAccess {
    Set<ChunkHolder> leafs$changedHolders();
}
