package fr.hardel.leafs.ticking;

/** Implemented onto {@code ServerChunkCache.MainThreadExecutor} by mixin: binds the level's ownership to the pump. */
public interface ChunkPumpAccess {

    void leafs$bindOwnership(LevelOwnership ownership);
}
