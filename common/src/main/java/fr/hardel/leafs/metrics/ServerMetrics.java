package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickFamily;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAccumulator;

/** The server-wide counters and the global tick stages, one instance per server, owned by the ticking manager. */
public final class ServerMetrics {
    private final MinuteCounter tickEventBorrows = new MinuteCounter();
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

    /** Loader server tick events emitted with a subscriber, each one a borrow scope on the server thread. */
    public MinuteCounter tickEventBorrows() {
        return tickEventBorrows;
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

    /** Generation steps the pool ran, by target status: against the FULL count, the work spent on chunks that never completed. */
    public void stepRan(ChunkStatus status) {
        stepsRan.incrementAndGet(status.getIndex());
    }

    public long stepsRan(ChunkStatus status) {
        return stepsRan.get(status.getIndex());
    }

    /** Chunks that ran their FULL step, the generation pipeline's output. */
    public MinuteCounter chunksFull() {
        return chunksFull;
    }

    /** A thread that needed an absent chunk and waited for it. */
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

    /** Times a thread waited for a player another thread held: before his tick joined that exclusion, the two ran on him together. */
    public MinuteCounter sharedPlayers() {
        return sharedPlayers;
    }
}
