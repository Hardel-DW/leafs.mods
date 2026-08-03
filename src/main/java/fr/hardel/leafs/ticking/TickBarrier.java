package fr.hardel.leafs.ticking;

/**
 * The global synchronisation point: {@link #raise()} returns once every tick that was in flight on
 * another thread has finished, and keeps new ticks parked until {@link #drop()}. Raisers exclude
 * each other so only one holder ever owns the raised window, and that holder may nest. When nothing
 * raises it, ticks pay two uncontended monitor operations - regions never stop.
 *
 * <p>The barrier can never stay raised: every method leaves it exactly as it found it when it exits
 * exceptionally, and the two ways a holder could block forever on itself (raising from inside its
 * own tick, ticking while holding) are rejected instead of parked.
 */
public final class TickBarrier {
    private final ThreadLocal<int[]> activeTickDepth = ThreadLocal.withInitial(() -> new int[1]);

    private Thread holder;
    private int holdDepth;
    private int activeTicks;

    public synchronized void enterTick() {
        Thread current = Thread.currentThread();
        while (holder != null) {
            if (holder == current) {
                throw new IllegalStateException("Thread '" + current.getName() + "' cannot start a tick while it holds the tick barrier");
            }

            waitUninterrupted();
        }

        activeTicks++;
        activeTickDepth.get()[0]++;
    }

    public synchronized void exitTick() {
        int[] depth = activeTickDepth.get();
        if (depth[0] == 0) {
            throw new IllegalStateException("Thread '" + Thread.currentThread().getName() + "' exited a tick it never entered");
        }

        depth[0]--;
        activeTicks--;
        notifyAll();
    }

    public synchronized void raise() {
        Thread current = Thread.currentThread();
        if (activeTickDepth.get()[0] > 0) {
            throw new IllegalStateException("Thread '" + current.getName() + "' cannot raise the tick barrier from inside its own tick");
        }

        while (holder != null && holder != current) {
            waitUninterrupted();
        }

        if (holder == current) {
            holdDepth++;
            return;
        }

        holder = current;
        holdDepth = 1;
        try {
            while (activeTicks > 0) {
                waitUninterrupted();
            }
        } catch (Throwable throwable) {
            holder = null;
            holdDepth = 0;
            notifyAll();
            throw throwable;
        }
    }

    public synchronized void drop() {
        if (holder == null) {
            throw new IllegalStateException("Tick barrier dropped without being raised");
        }
        if (holder != Thread.currentThread()) {
            throw new IllegalStateException("Tick barrier dropped by '" + Thread.currentThread().getName() + "' while held by '" + holder.getName() + "'");
        }

        if (--holdDepth == 0) {
            holder = null;
            notifyAll();
        }
    }

    private void waitUninterrupted() {
        try {
            wait();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting on the tick barrier", exception);
        }
    }
}
