package fr.hardel.leafs.ticking;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto {@code ServerChunkCache.MainThreadExecutor} by mixin: gives the pump its level for the universal-owner drain. */
public interface ChunkPumpAccess {

    void leafs$bindLevel(ServerLevel level);
}
