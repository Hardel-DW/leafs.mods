package fr.hardel.leafs.chunk;

public interface SavedEpochAccess {

    long leafs$savedEpoch();

    void leafs$markSaved(long epoch);
}
