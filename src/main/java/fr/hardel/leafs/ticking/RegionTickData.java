package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.world.RegionWorldData;

/** The per-region composite: its inbox, its clock and tick payload once the level equips it, a handle once the level activated. */
public final class RegionTickData {
    private final RegionInbox inbox;
    private volatile RegionTickHandle handle;
    private volatile RegionClock clock;
    private volatile RegionWorldData worldData;

    RegionTickData(long slowTaskNanos) {
        this.inbox = new RegionInbox(slowTaskNanos);
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
