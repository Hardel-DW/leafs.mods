package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionCrashReport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ConcurrentLinkedQueue;

/** The M3 synthetic region: one whole level, replaced by real regions once M7 feeds the regionizer. */
public final class LevelTickUnit extends TickHandle {
    private final ServerLevel level;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Runnable pendingWork;

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
        LevelTickPhases phases = TickingManager.phases();
        phases.beforeLevelTick(level);
        work.run();
        phases.afterLevelTick(level);
    }

    private void runQueuedTasks() {
        int budget = tasks.size();
        Runnable task;
        while (budget-- > 0 && (task = tasks.poll()) != null) {
            task.run();
        }
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        int entityCount = 0;
        for (Entity _ : level.getAllEntities()) {
            entityCount++;
        }

        return new RegionCrashReport(id(), dimension(), currentTick(), level.getChunkSource().getLoadedChunksCount(), entityCount);
    }
}
