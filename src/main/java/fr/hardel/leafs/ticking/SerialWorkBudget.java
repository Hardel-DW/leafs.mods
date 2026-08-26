package fr.hardel.leafs.ticking;

/** One time window per global tick, shared by every dimension's deferrable work; each consumer keeps a floor of progress. */
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
