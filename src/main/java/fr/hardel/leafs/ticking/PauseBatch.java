package fr.hardel.leafs.ticking;

/**
 * One region pause shared by every removal of the same connection tick: the first run raises the
 * barrier, the wave reuses it, the close releases it. Outside a batch each run pauses alone, the
 * previous behaviour. Global thread only, like the connection tick that opens it.
 */
public final class PauseBatch {
    private final TickBarrier barrier;
    private boolean open;
    private boolean held;

    PauseBatch(TickBarrier barrier) {
        this.barrier = barrier;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
        if (held) {
            held = false;
            barrier.drop();
        }
    }

    /** In a batch a failing action keeps the pause up for the rest of the wave; the close releases it. */
    public void run(Runnable action) {
        if (held) {
            action.run();
            return;
        }

        barrier.raise();
        if (open) {
            held = true;
            action.run();
            return;
        }

        try {
            action.run();
        } finally {
            barrier.drop();
        }
    }
}
