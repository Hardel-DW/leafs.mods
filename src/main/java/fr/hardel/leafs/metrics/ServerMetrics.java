package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickFamily;

/** The server-wide counters and the global tick stages, one instance per server, owned by the ticking manager. */
public final class ServerMetrics {
    private final BarrierStats barrier = new BarrierStats();
    private final DeferStats deferStats = new DeferStats();
    private final StageTimings globalStages = new StageTimings(TickStages.count(TickFamily.GLOBAL));
    private final MinuteCounter packetsIn = new MinuteCounter();
    private final MinuteCounter packetsOut = new MinuteCounter();
    private final MinuteCounter chunkLoads = new MinuteCounter();
    private final MinuteCounter chunkUnloads = new MinuteCounter();

    public BarrierStats barrier() {
        return barrier;
    }

    public DeferStats deferStats() {
        return deferStats;
    }

    public StageTimings globalStages() {
        return globalStages;
    }

    /** Inbound play packets, counted where the routing files them into a player's queue. */
    public MinuteCounter packetsIn() {
        return packetsIn;
    }

    /** Outbound packets, counted at the listener send choke point; raw connection sends bypass it. */
    public MinuteCounter packetsOut() {
        return packetsOut;
    }

    /** Chunk holders created; the load-side half of the churn. */
    public MinuteCounter chunkLoads() {
        return chunkLoads;
    }

    /** Unload decisions taken; the drop-side half of the churn. */
    public MinuteCounter chunkUnloads() {
        return chunkUnloads;
    }
}
