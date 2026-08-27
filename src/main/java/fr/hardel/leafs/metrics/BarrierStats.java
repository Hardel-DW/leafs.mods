package fr.hardel.leafs.metrics;

/** What still pauses every region: the Fabric tick events, until the barrier itself leaves. */
public final class BarrierStats {
    private final MinuteCounter fabricEventPauses = new MinuteCounter();

    public MinuteCounter fabricEventPauses() {
        return fabricEventPauses;
    }
}
