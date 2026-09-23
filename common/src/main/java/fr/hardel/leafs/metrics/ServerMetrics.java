package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickFamily;

import java.util.concurrent.atomic.LongAccumulator;

public final class ServerMetrics {
    private final DeferStats deferStats = new DeferStats();
    private final StageTimings globalStages = new StageTimings(TickStages.count(TickFamily.GLOBAL));
    private final MinuteCounter packetsIn = new MinuteCounter();
    private final MinuteCounter packetsOut = new MinuteCounter();
    private final MinuteCounter chunkLoads = new MinuteCounter();
    private final MinuteCounter chunkUnloads = new MinuteCounter();
    private final MinuteCounter chunksFull = new MinuteCounter();
    private final MinuteCounter sharedPlayers = new MinuteCounter();
    private final MinuteCounter chunkWaits = new MinuteCounter();
    private final LongAccumulator longestChunkWaitNanos = new LongAccumulator(Math::max, 0L);

    // Used by the Leafs Debug mod
    public DeferStats deferStats() {
        return deferStats;
    }

    // Used by the Leafs Debug mod
    public StageTimings globalStages() {
        return globalStages;
    }

    // Used by the Leafs Debug mod
    public MinuteCounter packetsIn() {
        return packetsIn;
    }

    // Used by the Leafs Debug mod
    public MinuteCounter packetsOut() {
        return packetsOut;
    }

    // Used by the Leafs Debug mod
    public MinuteCounter chunkLoads() {
        return chunkLoads;
    }

    // Used by the Leafs Debug mod
    public MinuteCounter chunkUnloads() {
        return chunkUnloads;
    }

    // Used by the Leafs Debug mod
    public MinuteCounter chunksFull() {
        return chunksFull;
    }

    public void chunkWaited(long nanos) {
        chunkWaits.increment();
        longestChunkWaitNanos.accumulate(nanos);
    }

    // Used by the Leafs Debug mod
    public MinuteCounter chunkWaits() {
        return chunkWaits;
    }

    // Used by the Leafs Debug mod
    public long longestChunkWaitNanos() {
        return longestChunkWaitNanos.get();
    }

    // Used by the Leafs Debug mod
    public MinuteCounter sharedPlayers() {
        return sharedPlayers;
    }
}
