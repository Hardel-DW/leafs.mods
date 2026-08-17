package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.RegionCrashReport;

/** One schedulable tick unit, subclassed by the whole-level attached tick and by a real region. */
public abstract class TickHandle {
    private final RegionContext context;
    private final TickTimings timings = new TickTimings();
    private volatile boolean cancelled;
    private volatile long currentTick;
    private volatile long scheduledStartNanos;

    protected TickHandle(RegionContext context) {
        this.context = context;
    }

    public long id() {
        return context.id();
    }

    public String dimension() {
        return context.dimension();
    }

    public long currentTick() {
        return currentTick;
    }

    public TickTimings timings() {
        return timings;
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

    void advance(long tickCount) {
        currentTick += tickCount;
    }

    long scheduledStartNanos() {
        return scheduledStartNanos;
    }

    void setScheduledStartNanos(long scheduledStartNanos) {
        this.scheduledStartNanos = scheduledStartNanos;
    }

    protected abstract void tick(long tickCount);

    protected abstract RegionCrashReport buildCrashReport();
}
