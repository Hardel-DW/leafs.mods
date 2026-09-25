package fr.hardel.leafs.chunk;

import java.util.function.IntSupplier;

public final class SectionStorageLock {

    public synchronized void runLocked(Runnable action) {
        action.run();
    }

    public synchronized int callLocked(IntSupplier action) {
        return action.getAsInt();
    }
}
