package fr.hardel.leafs.ticking;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class TickEpochs {
    private static final int SERVER = 0;
    private static final long CLOSED = Long.MAX_VALUE;

    private final AtomicLong clock = new AtomicLong();
    private final AtomicLongArray open;
    private final Runnable drain;

    public TickEpochs(int workers, Runnable drain) {
        this.open = new AtomicLongArray(workers + 1);
        this.drain = drain;
        for (int slot = 0; slot < open.length(); slot++) {
            open.set(slot, CLOSED);
        }
    }

    public int workers() {
        return open.length() - 1;
    }

    public void open(int worker) {
        open.set(worker, clock.get());
        clock.incrementAndGet();
    }

    // Each tick end asks for a drain of what is pending.
    public void close(int worker) {
        open.set(worker, CLOSED);
        drain.run();
    }

    public void openServer() {
        open(SERVER);
    }

    public void closeServer() {
        close(SERVER);
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
