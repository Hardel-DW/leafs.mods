package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import net.minecraft.server.level.ServerLevel;

public final class LevelTickUnit extends TickHandle {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final ServerLevel level;
    private final LevelRegions regions;
    private final RegionTickScheduler scheduler;
    private Runnable pendingWork;
    private boolean activated;
    private volatile int lastChunkCount;

    LevelTickUnit(long id, ServerLevel level, RegionTickScheduler scheduler) {
        super(new RegionContext.LevelSerial(id, level.dimension().identifier().toString()), TickStages.count(TickFamily.SERIAL));
        this.level = level;
        this.regions = LevelRegions.of(level);
        this.scheduler = scheduler;
    }

    // Used by the Leafs Debug mod
    public LevelRegions regions() {
        return regions;
    }

    void ensureActivated() {
        if (activated) {
            return;
        }

        activated = true;
        regions.activate(dimension(), scheduler, level::getGameTime, time -> RegionWorldData.regional(level, time), new RegionTickBody(level));
    }

    void prepareAttached(Runnable work) {
        pendingWork = work;
    }

    @Override
    public long currentTick() {
        return level.getGameTime();
    }

    @Override
    protected boolean tick() {
        Runnable work = pendingWork;
        if (work == null) {
            throw new IllegalStateException("Level tick unit ticked without prepared work");
        }

        pendingWork = null;
        StageTimings stages = stages();
        stages.beginTick(System.nanoTime());
        work.run();

        if (level.getGameTime() % CENSUS_INTERVAL_TICKS == 0) {
            lastChunkCount = level.getChunkSource().getLoadedChunksCount();
        }

        stages.mark(TickStages.serialManagement);
        stages.endTick(System.nanoTime());
        return true;
    }

    void tickPausedNetwork() {
        RegionContext.enter(context());
        try {
            RegionNetworkTick.drainPaused(level);
        } finally {
            RegionContext.exit();
        }
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
