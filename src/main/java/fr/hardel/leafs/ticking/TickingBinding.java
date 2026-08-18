package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import net.minecraft.server.level.ServerLevel;

/** The per-level implementation of the three deferral transports, resolved per call because the level activates later. */
public record TickingBinding(ServerLevel level) implements DeferredTransports {

    public static DeferredTransports of(ServerLevel level) {
        return new TickingBinding(level);
    }

    @Override
    public void toWindow(DeferReason reason, Runnable task) {
        BarrierWindow.of(level.getServer()).enqueue(reason, task);
    }

    @Override
    public void toSerial(DeferReason reason, Runnable task) {
        stats().countDeferral(reason);
        TickingManager.of(level.getServer()).submitToLevel(level, task);
    }

    @Override
    public void toOwner(DeferReason reason, int chunkX, int chunkZ, Runnable task) {
        stats().countDeferral(reason);
        scheduling().runOnOwner(chunkX, chunkZ, task);
    }

    @Override
    public boolean holdsWindow() {
        return BarrierWindow.of(level.getServer()).isDraining();
    }

    @Override
    public boolean holdsSerial() {
        return regions().ownership().isLevelSerialHeldByCurrentThread();
    }

    @Override
    public boolean owns(int chunkX, int chunkZ) {
        return scheduling().isOwner(chunkX, chunkZ);
    }

    @Override
    public void runDegraded(Runnable task) {
        DegradedChunkReads.run(task);
    }

    @Override
    public SharedChunkHolds holds() {
        return regions().holds();
    }

    @Override
    public DeferStats stats() {
        return TickingManager.of(level.getServer()).metrics().deferStats();
    }

    private ChunkScheduling scheduling() {
        return ((PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }

    private LevelRegions regions() {
        return LevelRegions.of(level);
    }
}
