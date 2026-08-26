package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.ChunkUnloadAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.RegionCrashReport;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** The schedulable side of one region. Both gates only try, a worker never parks; a skipped pass is a tick that never happened. */
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

                // Paused solo: the task lane still drains, like vanilla's main-thread queue.
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
                    unloadOwnChunks(body.level());
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

    /** The region lets its own chunks go: hidden entity chunks first, then the chunk decisions, then the teardowns queued so far. */
    private void unloadOwnChunks(ServerLevel level) {
        ((ServerLevelEntityAccess) level).leafs$entityPersistence().unloadHidden(chunkKey -> region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        ((ChunkUnloadAccess) level.getChunkSource().chunkMap).leafs$unloads().decideFor(region);
        regions.unloads().drain(region);
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), region.chunkCount(), region.data().entityData().tickList().size());
    }
}
