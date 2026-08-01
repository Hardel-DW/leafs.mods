package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionCrashReport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The M3 synthetic region: one whole level, still the only thing that ticks game state. Since M11a it
 * also drives the level's {@link LevelRegions} handshake once per tick, which is what lets real
 * regions split, shrink and die while the tick body is still level-wide.
 */
public final class LevelTickUnit extends TickHandle {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final ServerLevel level;
    private final LevelRegions regions;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Runnable pendingWork;
    private volatile int lastChunkCount;
    private volatile int lastTrackedChunks;
    private volatile int lastEntityCount;

    LevelTickUnit(long id, ServerLevel level) {
        super(id, level.dimension().identifier().toString());
        this.level = level;
        this.regions = ((ServerLevelRegionAccess) level).leafs$regions();
    }

    public LevelRegions regions() {
        return regions;
    }

    void prepareAttached(Runnable work) {
        pendingWork = work;
    }

    void submit(Runnable task) {
        tasks.add(task);
    }

    @Override
    protected void tick(long tickCount) {
        Runnable work = pendingWork;
        if (work == null) {
            throw new IllegalStateException("Level tick unit ticked without prepared work — free-running arrives with M11");
        }

        pendingWork = null;
        runQueuedTasks();
        for (LevelTickPhases phases : TickingManager.phases()) {
            phases.beforeLevelTick(level);
        }
        work.run();
        for (LevelTickPhases phases : TickingManager.phases()) {
            phases.afterLevelTick(level);
        }
        regions.settle();

        if (currentTick() % CENSUS_INTERVAL_TICKS == 0) {
            takeCensus();
        }
    }

    /** Counting entities walks every section, so it runs on the owner at a low rate and publishes for off-thread readers. */
    private void takeCensus() {
        lastChunkCount = level.getChunkSource().getLoadedChunksCount();
        lastTrackedChunks = regions.trackedChunks();
        int entities = 0;
        for (Entity _ : level.getAllEntities()) {
            entities++;
        }

        lastEntityCount = entities;
    }

    private void runQueuedTasks() {
        int budget = tasks.size();
        Runnable task;
        while (budget-- > 0 && (task = tasks.poll()) != null) {
            task.run();
        }
    }

    /** Last on-owner census; readable from any thread, at most {@value #CENSUS_INTERVAL_TICKS} ticks old. */
    public int chunkCount() {
        return lastChunkCount;
    }

    /** Region-side counterpart of {@link #chunkCount()}, sampled in the same census so the two are comparable. */
    public int trackedChunks() {
        return lastTrackedChunks;
    }

    public int entityCount() {
        return lastEntityCount;
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), chunkCount(), entityCount());
    }
}
