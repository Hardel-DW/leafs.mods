package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import net.minecraft.server.level.ServerLevel;

public final class LevelTickUnit {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final long id;
    private final String dimension;
    private final ServerLevel level;
    private final LevelRegions regions;
    private final StageTimings stages = new StageTimings(TickStages.count(TickFamily.SERIAL));
    private volatile int lastChunkCount;

    LevelTickUnit(long id, ServerLevel level, RegionTickScheduler scheduler) {
        this.id = id;
        this.dimension = level.dimension().identifier().toString();
        this.level = level;
        this.regions = LevelRegions.of(level);
        regions.activate(dimension, scheduler, level::getGameTime, time -> RegionWorldData.regional(level, time), new RegionTickBody(level));
    }

    // Used by the Leafs Debug mod
    public long id() {
        return id;
    }

    // Used by the Leafs Debug mod
    public String dimension() {
        return dimension;
    }

    // Used by the Leafs Debug mod
    public StageTimings stages() {
        return stages;
    }

    // Used by the Leafs Debug mod
    public LevelRegions regions() {
        return regions;
    }

    void tick(Runnable vanillaTick) {
        stages.beginTick(System.nanoTime());
        vanillaTick.run();
        if (level.getGameTime() % CENSUS_INTERVAL_TICKS == 0) {
            lastChunkCount = level.getChunkSource().getLoadedChunksCount();
        }

        stages.mark(TickStages.serialManagement);
        stages.endTick(System.nanoTime());
    }

    // Used by the Leafs Debug mod
    public int chunkCount() {
        return lastChunkCount;
    }

    // Used by the Leafs Debug mod
    public int entityCount() {
        int entities = 0;
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null && !handle.isCancelled()) {
                entities += handle.entityCount();
            }
        }

        return entities;
    }
}
