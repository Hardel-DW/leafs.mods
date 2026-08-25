package fr.hardel.leafs.ticking;

/** A region's own time, one tick per body pass; scheduled ticks are dated on it and only ever compared to it. */
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
