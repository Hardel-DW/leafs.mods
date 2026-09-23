package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickFamily;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAccumulator;

public final class ServerMetrics {
    private final DeferStats deferStats = new DeferStats();
    private final StageTimings globalStages = new StageTimings(TickStages.count(TickFamily.GLOBAL));
    private final MinuteCounter packetsIn = new MinuteCounter();
    private final MinuteCounter packetsOut = new MinuteCounter();
    private final MinuteCounter chunkLoads = new MinuteCounter();
    private final MinuteCounter chunkUnloads = new MinuteCounter();
    private final MinuteCounter chunksFull = new MinuteCounter();
    private final AtomicLongArray stepsRan = new AtomicLongArray(ChunkStatus.getStatusList().size());
    private final MinuteCounter sharedPlayers = new MinuteCounter();
    private final MinuteCounter chunkWaits = new MinuteCounter();
    private final AtomicLong chunkWaitNanos = new AtomicLong();
    private final LongAccumulator longestChunkWaitNanos = new LongAccumulator(Math::max, 0L);

    public DeferStats deferStats() {
        return deferStats;
    }

    public StageTimings globalStages() {
        return globalStages;
    }

    public MinuteCounter packetsIn() {
        return packetsIn;
    }

    public MinuteCounter packetsOut() {
        return packetsOut;
    }

    public MinuteCounter chunkLoads() {
        return chunkLoads;
    }

    public MinuteCounter chunkUnloads() {
        return chunkUnloads;
    }

    public void stepRan(ChunkStatus status) {
        stepsRan.incrementAndGet(status.getIndex());
    }

    public long stepsRan(ChunkStatus status) {
        return stepsRan.get(status.getIndex());
    }

    public MinuteCounter chunksFull() {
        return chunksFull;
    }

    public void chunkWaited(long nanos) {
        chunkWaits.increment();
        chunkWaitNanos.addAndGet(nanos);
        longestChunkWaitNanos.accumulate(nanos);
    }

    public MinuteCounter chunkWaits() {
        return chunkWaits;
    }

    public long chunkWaitNanos() {
        return chunkWaitNanos.get();
    }

    public long longestChunkWaitNanos() {
        return longestChunkWaitNanos.get();
    }

    public MinuteCounter sharedPlayers() {
        return sharedPlayers;
    }
}
