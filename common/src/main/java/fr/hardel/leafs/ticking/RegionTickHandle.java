package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;

import java.util.function.LongSupplier;

public final class RegionTickHandle extends TickHandle {
    private final Region<RegionTickData> region;
    private final LevelRegions regions;
    private volatile int chunkCensus;
    private volatile int entityCensus;
    private long lastEndNanos = System.nanoTime();

    RegionTickHandle(Region<RegionTickData> region, String dimension, LevelRegions regions, LongSupplier nanosPerTick) {
        super(region.id(), dimension, TickStages.count(TickFamily.REGION), nanosPerTick);
        this.region = region;
        this.regions = regions;
    }

    // Used by the Leafs Debug mod
    public int chunkCount() {
        return chunkCensus;
    }

    // Used by the Leafs Debug mod
    public int entityCount() {
        return entityCensus;
    }

    @Override
    public long currentTick() {
        return region.data().worldData().time().currentTick();
    }

    @Override
    protected long nextStartDelayNanos() {
        return region.data().worldData().time().nextStartDelayNanos();
    }

    @Override
    protected boolean tick() {
        if (!region.tryMarkTicking()) {
            return false;
        }

        try {
            tickMarked();
        } finally {
            region.markNotTicking();
        }

        return true;
    }

    private void tickMarked() {
        RegionTickData data = region.data();
        RegionWorldData worldData = data.worldData();
        RegionTickBody body = regions.body();
        WorldTickContext context = WorldTickContext.enter(body.level(), region, worldData);
        try {
            if (TickingManager.of(body.level().getServer()).state().paused()) {
                data.inbox().drain();
                return;
            }

            StageTimings stages = stages();
            long startNanos = System.nanoTime();
            stages.recordLag(startNanos - scheduledStartNanos());
            stages.beginTick(startNanos);
            long tasksDeadlineNanos = startNanos + scheduledStartNanos() - lastEndNanos;
            body.tick(region, worldData, stages, regions, tasksDeadlineNanos, startNanos + body.level().tickRateManager().nanosecondsPerTick());
            chunkCensus = region.chunkCount();
            entityCensus = worldData.entities().size();
            stages.endTick(System.nanoTime());
        } finally {
            lastEndNanos = System.nanoTime();
            context.exit();
        }
    }
}
