package fr.hardel.leafs.global;

import java.util.concurrent.atomic.LongAdder;

/**
 * Runtime signal for {@code /leafs recommendation}: repeating command blocks that keep opening the
 * barrier window. A striped counter and one volatile write per deferral, nothing a region worker
 * can contend on.
 */
public final class WindowPressure {
    private final LongAdder repeatingDeferrals = new LongAdder();
    private volatile long lastRepeatingDeferralNanos;

    public void recordRepeatingDeferral() {
        repeatingDeferrals.increment();
        lastRepeatingDeferralNanos = System.nanoTime();
    }

    public long repeatingDeferrals() {
        return repeatingDeferrals.sum();
    }

    /** Zero when no repeating command block has ever deferred. */
    public long lastRepeatingDeferralNanos() {
        return lastRepeatingDeferralNanos;
    }
}
