package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;

/** Reaches the level's Leafs propagator through its distance manager; null while neither driving nor shadowing. */
public interface PropagatorAccess {

    LevelTicketPropagator leafs$propagator();
}
