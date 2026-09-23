package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;

public abstract class TickHandle {
    private final RegionContext context;
    private final StageTimings stages;
    private volatile boolean cancelled;
    private volatile long scheduledStartNanos;

    protected TickHandle(RegionContext context, int stageCount) {
        this.context = context;
        this.stages = new StageTimings(stageCount);
    }

    public long id() {
        return context.id();
    }

    public String dimension() {
        return context.dimension();
    }

    public StageTimings stages() {
        return stages;
    }

    public void cancel() {
        cancelled = true;
    }

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

    public abstract long currentTick();

    protected abstract boolean tick();

    protected abstract RegionCrashReport buildCrashReport();
}
