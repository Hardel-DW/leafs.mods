package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.ChunkUnloadAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ServerLevel;
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

        boolean failed = false;
        try {
            RegionTickData data = region.data();
            RegionWorldData worldData = data.worldData();
            RegionTickBody body = regions.body();
            if (worldData == null || body == null) {
                return;
            }

            // Paused solo: the mail still drains, like vanilla's main-thread queue.
            if (body.level().getServer().isPaused()) {
                body.drainMail(region, worldData);
                return;
            }

            WorldTickContext.enter(body.level(), region, worldData);
            try {
                StageTimings stages = stages();
                long startNanos = System.nanoTime();
                stages.recordLag(startNanos - scheduledStartNanos());
                stages.beginTick(startNanos);
                unloadOwnChunks(body.level());
                stages.mark(TickStages.regionUnloads);
                body.tick(region, data.clock(), worldData, stages, regions);
                chunkCensus = region.chunkCount();
                entityCensus = worldData.entities().size();
                stages.endTick(System.nanoTime());
            } finally {
                WorldTickContext.exit();
            }
        } catch (Throwable throwable) {
            failed = true;
            throw throwable;
        } finally {
            if (!failed) {
                region.markNotTicking();
            }
        }
    }

    /** Still marked ticking from the failed pass: a pending merge cannot take the broken payload before it is dropped. */
    @Override
    protected boolean recover() {
        try {
            return regions.restart(region);
        } finally {
            region.markNotTicking();
        }
    }

    /** The region lets its own chunks go: hidden entity chunks first, then the chunk decisions; the teardowns run on the chunk workers after the save. */
    private void unloadOwnChunks(ServerLevel level) {
        ((ServerLevelEntityAccess) level).leafs$entityPersistence().unloadHidden(chunkKey -> region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        ((ChunkUnloadAccess) level.getChunkSource().chunkMap).leafs$unloads().decide(chunkKey -> region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), region.chunkCount(), entityCensus);
    }
}
