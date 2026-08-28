package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.ConcurrentLinkedQueue;

/** The server-thread remainder of the level tick. Activation schedules the regions before any of them ticks. */
public final class LevelTickUnit extends TickHandle {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final ServerLevel level;
    private final LevelRegions regions;
    private final RegionTickScheduler scheduler;
    private final SerialWorkBudget serialBudget;
    private final int slowTaskWarnMillis;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Runnable pendingWork;
    private boolean activated;
    private volatile int lastChunkCount;
    private volatile int lastViewChunks;

    LevelTickUnit(long id, ServerLevel level, RegionTickScheduler scheduler, SerialWorkBudget serialBudget, int slowTaskWarnMillis) {
        super(new RegionContext.LevelSerial(id, level.dimension().identifier().toString()), TickStages.count(TickFamily.SERIAL));
        this.level = level;
        this.regions = LevelRegions.of(level);
        this.scheduler = scheduler;
        this.serialBudget = serialBudget;
        this.slowTaskWarnMillis = slowTaskWarnMillis;
    }

    public LevelRegions regions() {
        return regions;
    }

    /** Server thread only, before the first tick of this level; no region is scheduled yet. */
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

    void submit(Runnable task) {
        tasks.add(task);
    }

    @Override
    public long currentTick() {
        return level.getGameTime();
    }

    @Override
    protected void tick() {
        Runnable work = pendingWork;
        if (work == null) {
            throw new IllegalStateException("Level tick unit ticked without prepared work");
        }

        pendingWork = null;
        StageTimings stages = stages();
        stages.beginTick(System.nanoTime());
        runQueuedTasks();
        stages.mark(TickStages.serialTasks);
        work.run();

        if (level.getGameTime() % CENSUS_INTERVAL_TICKS == 0) {
            lastChunkCount = level.getChunkSource().getLoadedChunksCount();
            lastViewChunks = ((PlayerLoaderAccess) level.getChunkSource().chunkMap).leafs$playerLoader().retainedChunks();
        }

        stages.mark(TickStages.serialManagement);
        stages.endTick(System.nanoTime());
    }

    /** Paused solo: vanilla drains packets while paused, so the per-player queues do too, without listener tick. */
    void tickPausedNetwork() {
        RegionContext.enter(context());
        try {
            RegionNetworkTick.drainPaused(level);
        } finally {
            RegionContext.exit();
        }
    }

    /** Time-boxed on the budget all dimensions share; at least one task runs, a slow one logs its class. */
    private void runQueuedTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            long start = System.nanoTime();
            task.run();
            long end = System.nanoTime();
            long millis = (end - start) / 1_000_000L;
            if (slowTaskWarnMillis > 0 && millis > slowTaskWarnMillis) {
                Leafs.LOGGER.warn("Level-serial task {} ran {} ms on {}", task.getClass().getName(), millis, dimension());
            }

            if (serialBudget.expired(end)) {
                break;
            }
        }
    }

    /** Last on-owner census; readable from any thread, at most {@value #CENSUS_INTERVAL_TICKS} ticks old. */
    public int chunkCount() {
        return lastChunkCount;
    }

    /** Chunks the player view pipelines retain a ticket on; a count that never falls back after a wave names a leak. */
    public int viewChunks() {
        return lastViewChunks;
    }

    /** Sum of the region censuses, O(regions), any thread. */
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

    @Override
    protected boolean recover() {
        return false;
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), chunkCount(), entityCount());
    }
}
