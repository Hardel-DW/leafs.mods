package fr.hardel.leafs.entity;

/** Implemented onto {@code Entity} by mixin: one thread ticks an entity at a time, the photo of another region skips it while its tick runs. */
public interface EntityTickAccess {

    /** False when another thread is ticking the entity right now. */
    boolean leafs$beginTick();

    void leafs$endTick();
}
