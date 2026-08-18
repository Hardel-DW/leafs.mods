package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.RegionStage;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.ownership.RegionContext;
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
        super(new RegionContext.Region(region.id(), dimension), RegionStage.values().length);
        this.region = region;
        this.regions = regions;
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

                // Vanilla runs its main-thread queue while paused; the task lane must too, or a paused solo join waits on its entity deliveries forever.
                if (body.level().getServer().isPaused()) {
                    regions.taskScheduler().drain(region);
                    return;
                }

                WorldTickContext.enter(body.level(), worldData, data.entityData());
                try {
                    StageTimings stages = stages();
                    stages.beginTick(System.nanoTime());
                    regions.taskScheduler().drain(region);
                    stages.mark(RegionStage.TASKS);
                    regions.unloads().drain(region);
                    stages.mark(RegionStage.UNLOADS);
                    body.tick(region, worldData, data.entityData(), tickCount, stages);
                    data.autosave().tick(body.level(), region, data.entityData(), regions.autosaveEpoch());
                    stages.mark(RegionStage.AUTOSAVE);
                    chunkCensus = region.chunkCount();
                    entityCensus = data.entityData().tickList().size();
                    stages.endTick();
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
