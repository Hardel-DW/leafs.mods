package fr.hardel.leafs.ticking;

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
