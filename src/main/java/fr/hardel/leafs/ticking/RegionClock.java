package fr.hardel.leafs.ticking;

/** The region's own time, one tick per pass, only ever compared to itself. */
public final class RegionClock {
    private long tick;

    public RegionClock(long startTick) {
        this.tick = startTick;
    }

    public long currentTick() {
        return tick;
    }

    public void advance() {
        tick++;
    }

    public void resetTo(long value) {
        tick = value;
    }
}
