package fr.hardel.leafs.ticking;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class TickEpochs {
    private static final int SERVER = 0;
    private static final long CLOSED = Long.MAX_VALUE;

    private final AtomicLong clock = new AtomicLong();
    private final AtomicLongArray open;
    private final Runnable drain;
    private volatile Thread server;
    // Only the server thread touches it.
    private boolean serverWrote;

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

    // A region worker drains what is pending at each tick end.
    public void close(int worker) {
        open.set(worker, CLOSED);
        drain.run();
    }

    public void openServer() {
        server = Thread.currentThread();
        open(SERVER);
    }

    // The server drains only after writing, so it never drains the writes of others.
    public void closeServer() {
        open.set(SERVER, CLOSED);
        if (!serverWrote) {
            return;
        }

        drain.run();
        serverWrote = false;
    }

    // False for a write no tick end will drain, made neither by a region worker nor by the server thread.
    public boolean claimWrite() {
        if (Thread.currentThread() != server) {
            return RegionTickScheduler.onWorker();
        }

        serverWrote = true;
        return true;
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
