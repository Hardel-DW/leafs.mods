package fr.hardel.leafs.metrics;

/** Deferral counters per reason: what crossed to an owner, and what was dropped at the destination. */
public final class DeferStats {
    private final MinuteCounter[] deferrals = counters();
    private final MinuteCounter[] drops = counters();

    private static MinuteCounter[] counters() {
        MinuteCounter[] built = new MinuteCounter[DeferReason.values().length];
        for (int index = 0; index < built.length; index++) {
            built[index] = new MinuteCounter();
        }

        return built;
    }

    public void countDeferral(DeferReason reason) {
        deferrals[reason.ordinal()].increment();
    }

    public void countDrop(DeferReason reason) {
        drops[reason.ordinal()].increment();
    }

    public MinuteCounter deferrals(DeferReason reason) {
        return deferrals[reason.ordinal()];
    }

    public MinuteCounter drops(DeferReason reason) {
        return drops[reason.ordinal()];
    }
}
