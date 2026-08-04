package fr.hardel.leafs.world;

import java.util.function.LongSupplier;

/** The region's relative-time source (two clocks, see Architecture): delegates to the level's game time, or counts on its own. */
public final class RegionClock {
    private final LongSupplier attachedSource;
    private long tick;

    public RegionClock(LongSupplier attachedSource) {
        this.attachedSource = attachedSource;
    }

    public RegionClock(long startTick) {
        this.attachedSource = null;
        this.tick = startTick;
    }

    public long currentTick() {
        return attachedSource != null ? attachedSource.getAsLong() : tick;
    }

    /** Region bodies only; a late region advances by every missed period at once (catch-up). */
    public void advance(long ticks) {
        if (attachedSource != null) {
            throw new IllegalStateException("An attached clock follows game time and cannot be advanced");
        }

        tick += ticks;
    }

    void resetTo(long value) {
        tick = value;
    }
}
