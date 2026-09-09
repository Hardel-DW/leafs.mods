package fr.hardel.leafs.chunk;

import java.util.function.IntSupplier;

/** One monitor per section storage for what no concurrent facade can carry: its dirty set, its save walk, and the graphs a subclass keeps over its sections. */
public final class SectionStorageLock {

    public synchronized void runLocked(Runnable action) {
        action.run();
    }

    public synchronized int callLocked(IntSupplier action) {
        return action.getAsInt();
    }
}
