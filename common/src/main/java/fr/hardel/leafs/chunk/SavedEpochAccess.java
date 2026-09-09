package fr.hardel.leafs.chunk;

/** Implemented onto {@code ChunkHolder} and {@code ServerPlayer} by mixin: the last autosave epoch that wrote this one out. */
public interface SavedEpochAccess {

    long leafs$savedEpoch();

    void leafs$markSaved(long epoch);
}
