package fr.hardel.leafs.chunk;

/** Implemented onto {@code DistanceManager} by mixin: built before the chunk system, bound to it right after. */
public interface DistanceManagerAccess {
    void leafs$bind(LevelChunks chunks);
}
