package fr.hardel.leafs.metrics;

import java.util.Arrays;

/**
 * Per-stage durations of one tick unit, written single-threaded by the owning tick loop with one
 * {@link #mark} between stages. Reads tolerate a torn row, so sampling never touches the tick path.
 */
public final class StageTimings {
    public static final int CAPACITY = 240;
    private final long[][] ring;
    private long[] row;
    private long lastMarkNanos;
    private volatile int cursor;

    public StageTimings(int stageCount) {
        this.ring = new long[CAPACITY][stageCount];
    }

    public void beginTick(long nowNanos) {
        row = ring[cursor % CAPACITY];
        Arrays.fill(row, 0);
        lastMarkNanos = nowNanos;
    }

    /** Attributes the time elapsed since the previous mark to {@code stage}; additive, a stage may be marked twice. */
    public void mark(TickStage stage) {
        mark(stage, System.nanoTime());
    }

    void mark(TickStage stage, long nowNanos) {
        if (row == null) {
            return;
        }

        row[stage.ordinal()] += nowNanos - lastMarkNanos;
        lastMarkNanos = nowNanos;
    }

    public void endTick() {
        row = null;
        cursor++;
    }

    public int stageCount() {
        return ring[0].length;
    }

    /** Average nanos per stage over the last completed ticks, capped to the window actually recorded. */
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
}
