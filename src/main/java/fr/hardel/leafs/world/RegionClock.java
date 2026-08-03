package fr.hardel.leafs.world;

import java.util.function.LongSupplier;

/**
 * The region's relative-time source (two clocks, see Architecture). Attached mode delegates to the
 * level's game time - identical values, zero drift - and M11 swaps in an independent per-region
 * counter; every relative-time consumer already reads through here, so nothing re-plumbs then.
 */
public final class RegionClock {
    private final LongSupplier attachedSource;

    public RegionClock(LongSupplier attachedSource) {
        this.attachedSource = attachedSource;
    }

    public long currentTick() {
        return attachedSource.getAsLong();
    }
}
