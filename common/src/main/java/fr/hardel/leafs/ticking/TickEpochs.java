package fr.hardel.leafs.ticking;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class TickEpochs {
    public static final int SERVER = 0;
    private static final long CLOSED = Long.MAX_VALUE;

    private final AtomicLong clock = new AtomicLong();
    private final AtomicLongArray open;

    public TickEpochs(int workers) {
        this.open = new AtomicLongArray(workers + 1);
        for (int slot = 0; slot < open.length(); slot++) {
            open.set(slot, CLOSED);
        }
    }

    public void open(int slot) {
        open.set(slot, clock.get());
        clock.incrementAndGet();
    }

    public void close(int slot) {
        open.set(slot, CLOSED);
    }

    public long now() {
        return clock.get();
    }

    // A write tagged below it outlived every tick that was open when it was written.
    public long oldestOpen() {
        long oldest = clock.get() + 1;
        for (int slot = 0; slot < open.length(); slot++) {
            oldest = Math.min(oldest, open.get(slot));
        }

        return oldest;
    }
}
