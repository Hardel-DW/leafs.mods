package fr.hardel.leafs.ticking;

/**
 * The shared time window of the global tick's deferrable serial work. Every dimension's task drain
 * and unload-decision loop consumes the same window, so the worst case stays constant no matter how
 * many dimensions the server or its mods run. Each consumer guarantees its own minimum progress, so
 * an exhausted window defers work to later ticks instead of starving a lane.
 */
public final class SerialWorkBudget {
    private static final long WINDOW_NANOS = 10_000_000L;

    private long deadlineNanos;

    public void beginTick(long nowNanos) {
        deadlineNanos = nowNanos + WINDOW_NANOS;
    }

    public boolean expired(long nowNanos) {
        return nowNanos >= deadlineNanos;
    }
}
