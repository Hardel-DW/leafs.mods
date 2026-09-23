package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;
import net.minecraft.CrashReportCategory;

public abstract class TickHandle {
    private final RegionContext context;
    private final StageTimings stages;
    private volatile boolean cancelled;
    private volatile long scheduledStartNanos;

    protected TickHandle(RegionContext context, int stageCount) {
        this.context = context;
        this.stages = new StageTimings(stageCount);
    }

    // Used by the Leafs Debug mod
    public long id() {
        return context.id();
    }

    // Used by the Leafs Debug mod
    public String dimension() {
        return context.dimension();
    }

    // Used by the Leafs Debug mod
    public StageTimings stages() {
        return stages;
    }

    public void cancel() {
        cancelled = true;
    }

    // Used by the Leafs Debug mod
    public boolean isCancelled() {
        return cancelled;
    }

    RegionContext context() {
        return context;
    }

    long scheduledStartNanos() {
        return scheduledStartNanos;
    }

    void setScheduledStartNanos(long scheduledStartNanos) {
        this.scheduledStartNanos = scheduledStartNanos;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Id", id()).setDetail("Dimension", dimension()).setDetail("Tick", currentTick());
    }

    public abstract long currentTick();

    protected abstract boolean tick();
}
