package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import net.minecraft.server.level.ServerLevel;

/** Hands ticking/'s per-level surfaces to entity/, which must not import ticking/. Resolved per call, the level activates later. */
public record TickingBinding(ServerLevel level) implements EntityTeleports.LevelBinding {

    @Override
    public SharedChunkHolds holds() {
        return regions().holds();
    }

    @Override
    public boolean currentRegionOwns(int chunkX, int chunkZ) {
        return ((PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager())
            .leafs$propagator().scheduling().currentRegionOwns(chunkX, chunkZ);
    }

    @Override
    public void submitSerial(Runnable task) {
        TickingManager.of(level.getServer()).submitToLevel(level, task);
    }

    @Override
    public void submitWindow(Runnable task) {
        BarrierWindow.of(level.getServer()).enqueue(task);
    }

    @Override
    public void submitPlacement(int chunkX, int chunkZ, Runnable placement) {
        RegionScheduler<RegionTickData> scheduler = regions().taskScheduler();
        if (scheduler != null) {
            scheduler.queue(chunkX, chunkZ, placement);
        } else {
            submitSerial(placement);
        }
    }

    private LevelRegions regions() {
        return LevelRegions.of(level);
    }
}
