package fr.hardel.leafs.metrics;

/**
 * What the barrier costs and why. The reason counters increment from any thread at enqueue time; the
 * opening ring is written by the global thread alone and read tolerating a torn sample, like {@code TickTimings}.
 */
public final class BarrierStats {
    private static final int CAPACITY = 256;
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final MinuteCounter[] reasons;
    private final MinuteCounter fabricEventPauses = new MinuteCounter();
    private final MinuteCounter disconnectPauses = new MinuteCounter();
    private final long[] endNanos = new long[CAPACITY];
    private final long[] durationNanos = new long[CAPACITY];
    private final int[] queueDepths = new int[CAPACITY];
    private volatile int cursor;

    public BarrierStats() {
        this.reasons = new MinuteCounter[WindowReason.values().length];
        for (int index = 0; index < reasons.length; index++) {
            reasons[index] = new MinuteCounter();
        }
    }

    public void countReason(WindowReason reason) {
        reasons[reason.ordinal()].increment();
    }

    public MinuteCounter reason(WindowReason reason) {
        return reasons[reason.ordinal()];
    }

    /** Raised by a Fabric server tick event emission, outside the window queue. */
    public MinuteCounter fabricEventPauses() {
        return fabricEventPauses;
    }

    /** Raised by a player disconnect wave, outside the window queue. */
    public MinuteCounter disconnectPauses() {
        return disconnectPauses;
    }

    public void recordOpen(long openEndNanos, long openDurationNanos, int queueDepth) {
        int index = cursor % CAPACITY;
        endNanos[index] = openEndNanos;
        durationNanos[index] = openDurationNanos;
        queueDepths[index] = queueDepth;
        cursor++;
    }

    /** The openings of the last minute: how many, how long, how deep the queue was. */
    public Sample sample(long nowNanos) {
        long cutoff = nowNanos - WINDOW_NANOS;
        int opens = 0;
        long total = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        int deepest = 0;
        for (int index = 0; index < CAPACITY; index++) {
            if (endNanos[index] >= cutoff && durationNanos[index] > 0) {
                opens++;
                total += durationNanos[index];
                min = Math.min(min, durationNanos[index]);
                max = Math.max(max, durationNanos[index]);
                deepest = Math.max(deepest, queueDepths[index]);
            }
        }

        if (opens == 0) {
            return Sample.IDLE;
        }

        return new Sample(opens, total / (double) opens / NANOS_PER_MILLI, min / NANOS_PER_MILLI, max / NANOS_PER_MILLI, deepest);
    }

    public record Sample(int opensPerMinute, double avgMs, double minMs, double maxMs, int deepestQueue) {
        static final Sample IDLE = new Sample(0, 0, 0, 0, 0);
    }
}
