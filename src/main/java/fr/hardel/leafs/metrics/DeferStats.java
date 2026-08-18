package fr.hardel.leafs.metrics;

import fr.hardel.leafs.ownership.OwnershipViolationException;

/**
 * The deferred-work and chunk-refusal counters, one instance per server. Deferrals count at the
 * transport, so an inline execution counts nothing; refusals count at the chunk contract, so a
 * FOREIGN rate above zero on a quiet server names an ownership leak that was invisible before.
 */
public final class DeferStats {
    public enum RefusalSource {
        REGION,
        SERIAL,
        FOREIGN_THREAD
    }

    private final MinuteCounter[] deferrals = counters(DeferReason.values().length);
    private final MinuteCounter[] retries = counters(DeferReason.values().length);
    private final MinuteCounter[] drops = counters(DeferReason.values().length);
    private final MinuteCounter[][] refusals;

    public DeferStats() {
        this.refusals = new MinuteCounter[OwnershipViolationException.Kind.values().length][];
        for (int kind = 0; kind < refusals.length; kind++) {
            refusals[kind] = counters(RefusalSource.values().length);
        }
    }

    private static MinuteCounter[] counters(int size) {
        MinuteCounter[] built = new MinuteCounter[size];
        for (int index = 0; index < size; index++) {
            built[index] = new MinuteCounter();
        }

        return built;
    }

    public void countDeferral(DeferReason reason) {
        deferrals[reason.ordinal()].increment();
    }

    public void countRetry(DeferReason reason) {
        retries[reason.ordinal()].increment();
    }

    public void countDrop(DeferReason reason) {
        drops[reason.ordinal()].increment();
    }

    public void countRefusal(OwnershipViolationException.Kind kind, RefusalSource source) {
        refusals[kind.ordinal()][source.ordinal()].increment();
    }

    public MinuteCounter deferrals(DeferReason reason) {
        return deferrals[reason.ordinal()];
    }

    public MinuteCounter retries(DeferReason reason) {
        return retries[reason.ordinal()];
    }

    public MinuteCounter drops(DeferReason reason) {
        return drops[reason.ordinal()];
    }

    public MinuteCounter refusals(OwnershipViolationException.Kind kind, RefusalSource source) {
        return refusals[kind.ordinal()][source.ordinal()];
    }
}
