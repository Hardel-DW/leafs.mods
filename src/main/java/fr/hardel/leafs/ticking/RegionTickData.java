package fr.hardel.leafs.ticking;

import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.scheduler.RegionTaskHost;
import fr.hardel.leafs.scheduler.RegionTaskQueues;
import fr.hardel.leafs.world.RegionAutosave;
import fr.hardel.leafs.world.RegionWorldData;

/** The per-region composite: queues and autosave for life, a tick payload that a crash renews, a handle once the level activated. */
public final class RegionTickData implements RegionTaskHost {
    private static final long DEATH_WINDOW_NANOS = 60_000_000_000L;
    private final RegionTaskQueues taskQueues = new RegionTaskQueues();
    private final RegionTaskQueues unloadQueues = new RegionTaskQueues();
    private final RegionAutosave autosave = new RegionAutosave();
    private volatile RegionTickHandle handle;
    private volatile RegionClock clock;
    private volatile RegionWorldData worldData;
    private volatile RegionEntityData entityData = new RegionEntityData();
    private long lastDeathNanos;

    @Override
    public RegionTaskQueues taskQueues() {
        return taskQueues;
    }

    @Override
    public RegionTaskQueues unloadQueues() {
        return unloadQueues;
    }

    public RegionAutosave autosave() {
        return autosave;
    }

    public RegionTickHandle handle() {
        return handle;
    }

    void attachHandle(RegionTickHandle handle) {
        this.handle = handle;
    }

    public RegionClock clock() {
        return clock;
    }

    public RegionWorldData worldData() {
        return worldData;
    }

    void equipWorld(RegionClock clock, RegionWorldData worldData) {
        this.clock = clock;
        this.worldData = worldData;
    }

    /** The payload a crashed tick left half-applied is dropped whole; the queues and the autosave survive. */
    void renewEntityData() {
        this.entityData = new RegionEntityData();
    }

    /** False on a second death inside the window: the region is not recoverable and the server stops as vanilla would. */
    boolean recordDeath(long nowNanos) {
        boolean recoverable = nowNanos - lastDeathNanos >= DEATH_WINDOW_NANOS;
        lastDeathNanos = nowNanos;
        return recoverable;
    }

    public RegionEntityData entityData() {
        return entityData;
    }
}
