package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.MinuteCounter;

/**
 * One region pause shared by every removal of the same connection tick: the first run raises the
 * barrier, the wave reuses it, the close releases it. Outside a batch each run pauses alone, the
 * previous behaviour. Global thread only, like the connection tick that opens it.
 */
public final class PauseBatch {
    private final TickBarrier barrier;
    private final MinuteCounter pauses;
    private boolean open;
    private boolean held;

    PauseBatch(TickBarrier barrier, MinuteCounter pauses) {
        this.barrier = barrier;
        this.pauses = pauses;
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

        pauses.increment();
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
