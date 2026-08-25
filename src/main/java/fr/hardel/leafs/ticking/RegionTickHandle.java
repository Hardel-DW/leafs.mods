package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.RegionCrashReport;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;

/**
 * The schedulable side of one region. Both gates only TRY: blocking here would let a raised barrier
 * or a level-serial holder park a pool worker. A skipped pass is a region tick that never happened.
 */
public final class RegionTickHandle extends TickHandle {
    private final Region<RegionTickData> region;
    private final LevelRegions regions;
    private volatile int chunkCensus;
    private volatile int entityCensus;

    RegionTickHandle(Region<RegionTickData> region, String dimension, LevelRegions regions) {
        super(new RegionContext.Region(region.id(), dimension), TickStages.count(TickFamily.REGION));
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
    public long currentTick() {
        return region.data().clock().currentTick();
    }

    @Override
    protected void tick() {
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
                    stages.mark(TickStages.regionTasks);
                    regions.unloads().drain(region);
                    stages.mark(TickStages.regionUnloads);
                    body.tick(region, data.clock(), worldData, data.entityData(), stages);
                    data.autosave().tick(body.level(), region, data.entityData(), regions.autosaveEpoch());
                    stages.mark(TickStages.regionAutosave);
                    chunkCensus = region.chunkCount();
                    entityCensus = data.entityData().tickList().size();
                    stages.endTick(System.nanoTime());
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
