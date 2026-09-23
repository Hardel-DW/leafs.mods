package fr.hardel.leafs.world;

public interface SavedEpochAccess {

    long leafs$savedEpoch();

    void leafs$markSaved(long epoch);
}
