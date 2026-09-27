package fr.hardel.leafs.ticking;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class TickEpochs {
    public static final int SERVER = 0;
    private static final long CLOSED = Long.MAX_VALUE;

    private final AtomicLong clock = new AtomicLong();
    private final AtomicLongArray open;
    private final Thread[] tickers;
    // Each flag is only touched by the thread of its slot.
    private final boolean[] wrote;
    private final Runnable written;

    public TickEpochs(int workers, Runnable written) {
        this.open = new AtomicLongArray(workers + 1);
        this.tickers = new Thread[workers + 1];
        this.wrote = new boolean[workers + 1];
        this.written = written;
        for (int slot = 0; slot < open.length(); slot++) {
            open.set(slot, CLOSED);
        }
    }

    public int workers() {
        return open.length() - 1;
    }

    public void open(int slot) {
        tickers[slot] = Thread.currentThread();
        open.set(slot, clock.get());
        clock.incrementAndGet();
    }

    // The closing thread drains what it wrote since its last close.
    public void close(int slot) {
        open.set(slot, CLOSED);
        if (!wrote[slot]) {
            return;
        }

        wrote[slot] = false;
        written.run();
    }

    // Marks the ticking thread that writes a level; false when none, so no tick end will drain the write.
    public boolean mark() {
        Thread current = Thread.currentThread();
        for (int slot = 0; slot < tickers.length; slot++) {
            if (tickers[slot] == current) {
                wrote[slot] = true;
                return true;
            }
        }

        return false;
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
