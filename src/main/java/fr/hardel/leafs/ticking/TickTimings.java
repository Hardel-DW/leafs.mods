package fr.hardel.leafs.ticking;

import java.util.Arrays;

/**
 * Ring of recent tick durations, written single-threaded by the owning tick loop. Reads tolerate a
 * torn sample, so {@link #sample} needs no lock and never touches the tick path.
 */
public final class TickTimings {
    private static final int CAPACITY = 256;
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

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
        long[] window = new long[CAPACITY];
        int ticks = 0;
        long total = 0;
        for (int index = 0; index < CAPACITY; index++) {
            if (endNanos[index] >= cutoff && durationNanos[index] > 0) {
                window[ticks++] = durationNanos[index];
                total += durationNanos[index];
            }
        }
        if (ticks == 0) {
            return Snapshot.IDLE;
        }

        Arrays.sort(window, 0, ticks);
        double tps = Math.min(ticks / (WINDOW_NANOS / 1_000_000_000.0), 20.0);

        return new Snapshot(tps, total / (double) ticks / NANOS_PER_MILLI, percentile(window, ticks, 0.50),
            percentile(window, ticks, 0.95), percentile(window, ticks, 0.99), window[ticks - 1] / NANOS_PER_MILLI);
    }

    /** Nearest-rank on the window: with ~100 samples per 5s window, interpolation would be false precision. */
    private static double percentile(long[] sorted, int count, double fraction) {
        int rank = Math.clamp((long) Math.ceil(fraction * count) - 1, 0, count - 1);

        return sorted[rank] / NANOS_PER_MILLI;
    }

    public record Snapshot(double tps, double msptAverage, double mspt50, double mspt95, double mspt99, double msptMax) {
        static final Snapshot IDLE = new Snapshot(0, 0, 0, 0, 0, 0);
    }
}
