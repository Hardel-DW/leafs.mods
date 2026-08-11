package fr.hardel.leafs.ticking;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Owns game context on one level: regions share it while mid-tick, the level-serial side is
 * exclusive. Region ticks only ever TRY, so an exclusive taker waits at most one in-flight tick body.
 */
public final class LevelOwnership {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object inlineMonitor = new Object();

    public boolean tryEnterRegionTick() {
        if (lock.isWriteLocked() || lock.hasQueuedThreads()) {
            return false;
        }

        return lock.readLock().tryLock();
    }

    public void exitRegionTick() {
        lock.readLock().unlock();
    }

    public void enterLevelSerial() {
        lock.writeLock().lock();
    }

    public void exitLevelSerial() {
        lock.writeLock().unlock();
    }

    public boolean isLevelSerialHeldByCurrentThread() {
        return lock.isWriteLockedByCurrentThread();
    }

    public boolean isRegionTickHeldByCurrentThread() {
        return lock.getReadHoldCount() > 0;
    }

    /**
     * Runs the action under this level's game context: reentrant for the serial holder, in place for
     * a region mid-tick (positional ownership covers its reach), exclusive for everyone else. Only
     * the server thread and foreign threads ever block here; region workers never do.
     */
    public void runExclusive(Runnable action) {
        callExclusive(() -> {
            action.run();

            return null;
        });
    }

    public <T> T callExclusive(Supplier<T> action) {
        if (isLevelSerialHeldByCurrentThread() || isRegionTickHeldByCurrentThread()) {
            return action.get();
        }

        enterLevelSerial();
        try {
            return action.get();
        } finally {
            exitLevelSerial();
        }
    }

    /**
     * Same as {@link #runExclusive}, plus a monitor around the inline branch: two region workers
     * share the read side and would otherwise interleave on level-wide state their positions do not
     * cover, like the player maps a join or a disconnect mutates.
     */
    public void runExclusiveSerialized(Runnable action) {
        callExclusive(() -> {
            synchronized (inlineMonitor) {
                action.run();
            }

            return null;
        });
    }
}
