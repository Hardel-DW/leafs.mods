package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionCrashReport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ConcurrentLinkedQueue;

/** The M3 synthetic region: one whole level, replaced by real regions once M7 feeds the regionizer. */
public final class LevelTickUnit extends TickHandle {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final ServerLevel level;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Runnable pendingWork;
    private volatile int lastChunkCount;
    private volatile int lastEntityCount;

    LevelTickUnit(long id, ServerLevel level) {
        super(id, level.dimension().identifier().toString());
        this.level = level;
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

        if (currentTick() % CENSUS_INTERVAL_TICKS == 0) {
            takeCensus();
        }
    }

    /** Counting entities walks every section, so it runs on the owner at a low rate and publishes for off-thread readers. */
    private void takeCensus() {
        lastChunkCount = level.getChunkSource().getLoadedChunksCount();
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

    public int entityCount() {
        return lastEntityCount;
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), chunkCount(), entityCount());
    }
}
