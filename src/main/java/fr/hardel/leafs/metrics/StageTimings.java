package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickStage;

import java.util.Arrays;

/** Per-tick ring of one unit, stage durations plus tick length; single writer, torn reads tolerated, skipped passes count for nothing. */
public final class StageTimings {
    public static final int CAPACITY = 240;
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final long[][] ring;
    private final long[] endNanos = new long[CAPACITY];
    private final long[] durationNanos = new long[CAPACITY];
    private long[] row;
    private long beginNanos;
    private long lastMarkNanos;
    private volatile int cursor;

    public StageTimings(int stageCount) {
        this.ring = new long[CAPACITY][stageCount];
    }

    public void beginTick(long nowNanos) {
        row = ring[cursor % CAPACITY];
        Arrays.fill(row, 0);
        beginNanos = nowNanos;
        lastMarkNanos = nowNanos;
    }

    /** Time since the previous mark goes to this stage; additive. */
    public void mark(TickStage stage) {
        mark(stage, System.nanoTime());
    }

    void mark(TickStage stage, long nowNanos) {
        if (row == null) {
            return;
        }

        row[stage.index()] += nowNanos - lastMarkNanos;
        lastMarkNanos = nowNanos;
    }

    public void endTick(long nowNanos) {
        int index = cursor % CAPACITY;
        endNanos[index] = nowNanos;
        durationNanos[index] = nowNanos - beginNanos;
        row = null;
        cursor++;
    }

    public int stageCount() {
        return ring[0].length;
    }

    /** Average nanos per stage over the last ticks. */
    public long[] averageNanos(int ticks) {
        int end = cursor;
        int count = Math.min(ticks, Math.min(end, CAPACITY - 1));
        long[] averages = new long[stageCount()];
        if (count == 0) {
            return averages;
        }

        for (int index = end - count; index < end; index++) {
            long[] sample = ring[Math.floorMod(index, CAPACITY)];
            for (int stage = 0; stage < averages.length; stage++) {
                averages[stage] += sample[stage];
            }
        }

        for (int stage = 0; stage < averages.length; stage++) {
            averages[stage] /= count;
        }

        return averages;
    }

    /** TPS and tick length over the last five seconds. */
    public Snapshot sample(long nowNanos) {
        long cutoff = nowNanos - WINDOW_NANOS;
        long[] window = new long[CAPACITY];
        int ticks = 0;
        long total = 0;
        long oldestEnd = nowNanos;
        for (int index = 0; index < CAPACITY; index++) {
            if (endNanos[index] >= cutoff && durationNanos[index] > 0) {
                window[ticks++] = durationNanos[index];
                total += durationNanos[index];
                oldestEnd = Math.min(oldestEnd, endNanos[index]);
            }
        }

        if (ticks == 0) {
            return Snapshot.IDLE;
        }

        Arrays.sort(window, 0, ticks);
        double spanSeconds = Math.max((nowNanos - oldestEnd) / 1_000_000_000.0, 1.0 / 20.0);
        double tps = Math.min(ticks / spanSeconds, 20.0);
        return new Snapshot(tps, total / (double) ticks / NANOS_PER_MILLI, percentile(window, ticks, 0.50), percentile(window, ticks, 0.95), percentile(window, ticks, 0.99), window[ticks - 1] / NANOS_PER_MILLI);
    }

    /** Nearest rank, ~100 samples make interpolation false precision. */
    private static double percentile(long[] sorted, int count, double fraction) {
        int rank = Math.clamp((long) Math.ceil(fraction * count) - 1, 0, count - 1);

        return sorted[rank] / NANOS_PER_MILLI;
    }

    public record Snapshot(double tps, double msptAverage, double mspt50, double mspt95, double mspt99, double msptMax) {
        static final Snapshot IDLE = new Snapshot(0, 0, 0, 0, 0, 0);
    }
}
