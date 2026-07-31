package fr.hardel.leafs.ticking;

/**
 * The global synchronisation point: {@link #raise()} returns once every in-flight tick finished and
 * keeps new ticks parked until {@link #drop()}. When nothing raises it, ticks pay two uncontended
 * monitor operations — regions never stop.
 */
public final class TickBarrier {
    private boolean raised;
    private int activeTicks;

    public synchronized void enterTick() {
        while (raised) {
            waitUninterrupted();
        }
        activeTicks++;
    }

    public synchronized void exitTick() {
        if (activeTicks == 0) {
            throw new IllegalStateException("Tick barrier exited more often than entered");
        }

        activeTicks--;
        notifyAll();
    }

    public synchronized void raise() {
        if (raised) {
            throw new IllegalStateException("Tick barrier raised twice");
        }

        raised = true;
        while (activeTicks > 0) {
            waitUninterrupted();
        }
    }

    public synchronized void drop() {
        if (!raised) {
            throw new IllegalStateException("Tick barrier dropped without being raised");
        }

        raised = false;
        notifyAll();
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
