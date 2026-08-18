package fr.hardel.leafs.ticking;

import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.scheduler.RegionTaskHost;
import fr.hardel.leafs.scheduler.RegionTaskQueues;
import fr.hardel.leafs.world.RegionAutosave;
import fr.hardel.leafs.world.RegionWorldData;

/** The per-region composite: task queues always, tick handle and the world/entity payloads once the level activated. */
public final class RegionTickData implements RegionTaskHost {
    private final RegionTaskQueues taskQueues = new RegionTaskQueues();
    private final RegionTaskQueues unloadQueues = new RegionTaskQueues();
    private final RegionAutosave autosave = new RegionAutosave();
    private volatile RegionTickHandle handle;
    private volatile RegionWorldData worldData;
    private volatile RegionEntityData entityData;

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

    public RegionWorldData worldData() {
        return worldData;
    }

    void attachWorldData(RegionWorldData worldData) {
        this.worldData = worldData;
    }

    public RegionEntityData entityData() {
        return entityData;
    }

    void attachEntityData(RegionEntityData entityData) {
        this.entityData = entityData;
    }
}
