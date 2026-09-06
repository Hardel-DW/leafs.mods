package fr.hardel.leafs.ticking;

import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.world.level.ChunkPos;

/** The schedulable side of one region; the gate only tries, a worker never parks, a skipped pass is a tick that never happened. */
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
        if (!region.tryMarkTicking()) {
            return;
        }

        try {
            RegionTickData data = region.data();
            RegionWorldData worldData = data.worldData();
            RegionTickBody body = regions.body();
            if (worldData == null || body == null) {
                return;
            }

            WorldTickContext.enter(body.level(), region, worldData);
            try {
                if (body.level().getServer().isPaused()) {
                    data.inbox().drain();
                    return;
                }

                StageTimings stages = stages();
                long startNanos = System.nanoTime();
                stages.recordLag(startNanos - scheduledStartNanos());
                stages.beginTick(startNanos);
                ((ServerLevelEntityAccess) body.level()).leafs$entityPersistence().unloadHidden(chunkKey -> region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
                stages.mark(TickStages.regionUnloads);
                body.tick(region, data.clock(), worldData, stages, regions, startNanos + regions.tickPeriodNanos());
                chunkCensus = region.chunkCount();
                entityCensus = worldData.entities().size();
                stages.endTick(System.nanoTime());
            } finally {
                WorldTickContext.exit();
            }
        } finally {
            region.markNotTicking();
        }
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), region.chunkCount(), entityCensus);
    }
}
