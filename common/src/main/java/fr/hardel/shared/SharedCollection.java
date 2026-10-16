package fr.hardel.shared;

/** A collection every game thread can use: each public method holds its monitor, and iteration walks a copy. */
public interface SharedCollection {
    Object leafs$snapshot();

    void leafs$changed();
}
