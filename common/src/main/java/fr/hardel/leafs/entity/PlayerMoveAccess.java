package fr.hardel.leafs.entity;

/** Implemented onto {@code ServerPlayer} by mixin: when the player last moved, read by every region's tracking pass. */
public interface PlayerMoveAccess {

    void leafs$markMoved();

    long leafs$movedNanos();
}
