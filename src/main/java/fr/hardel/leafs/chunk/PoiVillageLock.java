package fr.hardel.leafs.chunk;

import java.util.function.IntSupplier;

/** One monitor per POI storage for the village distance graph, the dirty set and the save walk, which no concurrent facade can carry. */
public final class PoiVillageLock {

    public synchronized void runLocked(Runnable action) {
        action.run();
    }

    public synchronized int callLocked(IntSupplier action) {
        return action.getAsInt();
    }
}
