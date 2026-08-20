package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.chunk.propagator.SimulationLevels;

/** Reaches the level's Leafs distance authorities through its distance manager; null while neither driving nor shadowing. */
public interface PropagatorAccess {

    LevelTicketPropagator leafs$propagator();

    SimulationLevels leafs$simulation();

    SpawnProximity leafs$spawnProximity();
}
