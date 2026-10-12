package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.StageTimings;
import net.minecraft.CrashReportCategory;

import java.util.function.LongSupplier;

public abstract class TickHandle {
    private final long id;
    private final String dimension;
    private final StageTimings stages;
    private volatile boolean cancelled;
    private volatile long scheduledStartNanos;

    protected TickHandle(long id, String dimension, int stageCount, LongSupplier nanosPerTick) {
        this.id = id;
        this.dimension = dimension;
        this.stages = new StageTimings(stageCount, nanosPerTick);
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

    public void cancel() {
        cancelled = true;
    }

    // Used by the Leafs Debug mod
    public boolean isCancelled() {
        return cancelled;
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

    protected abstract long nextStartDelayNanos();

    protected abstract boolean tick();
}
