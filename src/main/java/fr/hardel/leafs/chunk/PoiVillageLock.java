package fr.hardel.leafs.chunk;

import java.util.function.IntSupplier;

/**
 * The #22b residue lock: one monitor per POI storage serializing the village distance tracker, the
 * dirty-mark set and the budgeted save iteration - graph state and insertion-ordered iteration that
 * no drop-in facade can carry. Village-distance consumers are rare, so contention stays negligible.
 */
public final class PoiVillageLock {

    public synchronized void runLocked(Runnable action) {
        action.run();
    }

    public synchronized int callLocked(IntSupplier action) {
        return action.getAsInt();
    }
}
