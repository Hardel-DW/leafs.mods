package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickStage;

import java.util.Arrays;
import java.util.function.LongSupplier;

public final class StageTimings {
    public static final int CAPACITY = 240;
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long[][] ring;
    private final long[] endNanos = new long[CAPACITY];
    private final long[] durationNanos = new long[CAPACITY];
    private final LongSupplier periodNanos;
    private long[] row;
    private long beginNanos;
    private long lastMarkNanos;
    private long firstBeginNanos;
    private volatile boolean begun;
    private volatile int cursor;
    private volatile long lagNanos;
    private volatile long missedStarts;

    public StageTimings(int stageCount, LongSupplier periodNanos) {
        this.ring = new long[CAPACITY][stageCount];
        this.periodNanos = periodNanos;
    }

    public void beginTick(long nowNanos) {
        if (!begun) {
            firstBeginNanos = nowNanos;
            begun = true;
        }

        row = ring[cursor % CAPACITY];
        Arrays.fill(row, 0);
        beginNanos = nowNanos;
        lastMarkNanos = nowNanos;
    }

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

    public void recordLag(long nanos) {
        lagNanos += nanos;
    }

    public long lagNanos() {
        return lagNanos;
    }

    public void recordMissedStart() {
        missedStarts++;
    }

    public long missedStarts() {
        return missedStarts;
    }

    // Used by the Leafs Debug mod
    public int stageCount() {
        return ring[0].length;
    }

    // Used by the Leafs Debug mod
    public int completedTicks() {
        return cursor;
    }

    // Used by the Leafs Debug mod
    public long[][] rowsSince(int fromTick) {
        int end = cursor;
        int start = Math.max(fromTick, end - (CAPACITY - 1));
        long[][] rows = new long[Math.max(0, end - start)][];
        for (int index = start; index < end; index++) {
            rows[index - start] = ring[Math.floorMod(index, CAPACITY)].clone();
        }

        return rows;
    }

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

    // Used by the Leafs Debug mod
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

        double tps = tps(nowNanos, cutoff, ticks, oldestEnd);
        if (ticks == 0) {
            return new Snapshot(tps, 0, 0, 0, 0, 0);
        }

        Arrays.sort(window, 0, ticks);
        return new Snapshot(tps, total / (double) ticks / NANOS_PER_MILLI, percentile(window, ticks, 0.50), percentile(window, ticks, 0.95), percentile(window, ticks, 0.99), window[ticks - 1] / NANOS_PER_MILLI);
    }

    private double tps(long nowNanos, long cutoff, int ticks, long oldestEnd) {
        long period = periodNanos.getAsLong();
        double target = NANOS_PER_SECOND / period;
        if (!begun) {
            return target;
        }

        boolean young = firstBeginNanos >= cutoff;
        int events = young ? ticks + 1 : ticks;
        long since = young ? firstBeginNanos : oldestEnd;
        return Math.min(events * NANOS_PER_SECOND / Math.max(nowNanos - since, period), target);
    }

    private static double percentile(long[] sorted, int count, double fraction) {
        int rank = Math.clamp((long) Math.ceil(fraction * count) - 1, 0, count - 1);

        return sorted[rank] / NANOS_PER_MILLI;
    }

    // Used by the Leafs Debug mod
    public record Snapshot(double tps, double msptAverage, double mspt50, double mspt95, double mspt99, double msptMax) {
    }
}
