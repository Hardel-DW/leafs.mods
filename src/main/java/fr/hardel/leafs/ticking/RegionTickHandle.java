package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionCrashReport;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;

/**
 * The schedulable side of one region. Both gates only TRY: blocking here would let a raised barrier
 * or a level-serial holder park a pool worker. A skipped period is caught up by the scheduler's tick counting.
 */
public final class RegionTickHandle extends TickHandle {
    private final Region<RegionTickData> region;
    private final LevelRegions regions;
    private volatile int chunkCensus;
    private volatile int entityCensus;

    RegionTickHandle(Region<RegionTickData> region, String dimension, LevelRegions regions) {
        super(region.id(), dimension);
        this.region = region;
        this.regions = regions;
    }

    public Region<RegionTickData> region() {
        return region;
    }

    public int chunkCount() {
        return chunkCensus;
    }

    public int entityCount() {
        return entityCensus;
    }

    @Override
    protected void tick(long tickCount) {
        if (!regions.ownership().tryEnterRegionTick()) {
            return;
        }

        try {
            if (!region.tryMarkTicking()) {
                return;
            }

            try {
                RegionTickData data = region.data();
                RegionWorldData worldData = data.worldData();
                RegionTickBody body = regions.body();
                if (worldData == null || body == null) {
                    regions.taskScheduler().drain(region);

                    return;
                }

                if (body.level().getServer().isPaused()) {
                    return;
                }

                WorldTickContext.enter(body.level(), worldData);
                try {
                    regions.taskScheduler().drain(region);
                    body.tick(region, worldData, data.entityData(), tickCount);
                    chunkCensus = region.chunkCount();
                    entityCensus = data.entityData().tickList().size();
                } finally {
                    WorldTickContext.exit();
                }
            } finally {
                region.markNotTicking();
            }
        } finally {
            regions.ownership().exitRegionTick();
        }
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), region.chunkCount(), region.data().entityData().tickList().size());
    }
}
