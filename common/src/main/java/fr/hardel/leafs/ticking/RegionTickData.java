package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.world.RegionWorldData;

import java.util.function.Supplier;

/** The per-region composite: its inbox, its clock and tick payload once the level equips it, a handle once the level activated. */
public final class RegionTickData {
    private final RegionInbox inbox;
    private volatile RegionTickHandle handle;
    private volatile RegionClock clock;
    private volatile RegionWorldData worldData;

    /** The inbox runs what the region owns, ring included; a task on a section the region lost goes back through the owners. */
    RegionTickData(Region<RegionTickData> region, long slowTaskNanos, Supplier<ChunkOwners> owners) {
        this.inbox = new RegionInbox(slowTaskNanos, posted -> region.owns(posted.chunkX(), posted.chunkZ()),
            posted -> owners.get().submit(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task()));
    }

    public RegionInbox inbox() {
        return inbox;
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
}
