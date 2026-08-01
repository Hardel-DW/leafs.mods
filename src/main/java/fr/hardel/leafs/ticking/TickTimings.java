package fr.hardel.leafs.ticking;

/**
 * Ring of recent tick durations for one tick unit, written by its tick loop and read by /regions —
 * writes are single-threaded (the owning tick), reads tolerate a torn sample.
 */
public final class TickTimings {
    private static final int CAPACITY = 256;
    private static final long WINDOW_NANOS = 5_000_000_000L;

    private final long[] endNanos = new long[CAPACITY];
    private final long[] durationNanos = new long[CAPACITY];
    private volatile int cursor;

    void record(long tickEndNanos, long tickDurationNanos) {
        int index = cursor % CAPACITY;
        endNanos[index] = tickEndNanos;
        durationNanos[index] = tickDurationNanos;
        cursor++;
    }

    public Snapshot sample(long nowNanos) {
        long cutoff = nowNanos - WINDOW_NANOS;
        int ticks = 0;
        long total = 0;
        long max = 0;
        for (int index = 0; index < CAPACITY; index++) {
            if (endNanos[index] >= cutoff && durationNanos[index] > 0) {
                ticks++;
                total += durationNanos[index];
                max = Math.max(max, durationNanos[index]);
            }
        }
        if (ticks == 0) {
            return new Snapshot(0, 0, 0);
        }

        double tps = ticks / (WINDOW_NANOS / 1_000_000_000.0);

        return new Snapshot(Math.min(tps, 20.0), total / (double) ticks / 1_000_000.0, max / 1_000_000.0);
    }

    public record Snapshot(double tps, double msptAverage, double msptMax) {
    }
}
