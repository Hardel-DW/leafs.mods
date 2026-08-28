package fr.hardel.leafs.chunk.propagator;

/** A chunk entering or leaving simulation, told under the drained area's ticket lock, strictly alternating per chunk. */
public interface SimulationListener {

    void simulated(int chunkX, int chunkZ);

    void unsimulated(int chunkX, int chunkZ);
}
