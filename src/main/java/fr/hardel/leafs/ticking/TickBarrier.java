package fr.hardel.leafs.ticking;

/**
 * Global sync point: {@link #raise()} blocks until every in-flight tick finishes, then parks new
 * ticks until {@link #drop()}. Raising from inside a tick, or ticking while holding, is rejected rather than deadlocked.
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

    public synchronized boolean isHeldByCurrentThread() {
        return holder == Thread.currentThread();
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
