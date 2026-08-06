package fr.hardel.leafs.global;

import java.util.concurrent.atomic.LongAdder;

/** Runtime signal for {@code /leafs recommendation}: counts repeating command block deferrals. */
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
