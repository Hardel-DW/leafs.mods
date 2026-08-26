package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.global.SyncWindow;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.scheduler.DeferredTransports;
import net.minecraft.server.level.ServerLevel;

/** The per-level implementation of the deferral transports, resolved per call because the level activates later. */
public record TickingBinding(ServerLevel level) implements DeferredTransports {

    public static DeferredTransports of(ServerLevel level) {
        return new TickingBinding(level);
    }

    @Override
    public void toWindow(Runnable task) {
        SyncWindow.of(level.getServer()).enqueue(task);
    }

    @Override
    public void toOwner(int chunkX, int chunkZ, Runnable task) {
        scheduling().runOnOwner(chunkX, chunkZ, task);
    }

    @Override
    public boolean holdsWindow() {
        return SyncWindow.of(level.getServer()).isDraining();
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
    public DeferStats stats() {
        return TickingManager.of(level.getServer()).metrics().deferStats();
    }

    private ChunkScheduling scheduling() {
        return ((PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }

}
