package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;

public final class Region<R> {
    private final long id;
    private final Regionizer<R> regionizer;
    private final R data;

    final LongOpenHashSet sectionKeys = new LongOpenHashSet();
    final LongOpenHashSet deadSectionKeys = new LongOpenHashSet();
    final Set<Region<R>> mergeIntoLater = new LinkedHashSet<>();
    final Set<Region<R>> expectingMergeFrom = new LinkedHashSet<>();
    final Queue<RegionSection<R>> changed = new ConcurrentLinkedQueue<>();
    // Out of the schedule until its merge into a ticking region: a thread may still hold it meanwhile.
    boolean waitsForMerge;

    private volatile RegionState state = RegionState.READY;
    private volatile Thread tickingThread;

    Region(long id, Regionizer<R> regionizer, RegionCallbacks<R> callbacks) {
        this.id = id;
        this.regionizer = regionizer;
        this.data = callbacks.createData(this);
    }

    // Used by the Leafs Debug mod
    public long id() {
        return id;
    }

    // Used by the Leafs Debug mod
    public R data() {
        return data;
    }

    // Used by the Leafs Debug mod
    public RegionState state() {
        return state;
    }

    // Used by the Leafs Debug mod
    public Thread tickingThread() {
        return tickingThread;
    }

    public boolean tryMarkTicking() {
        return regionizer.tryMarkTicking(this, false);
    }

    public boolean tryHold() {
        return regionizer.tryMarkTicking(this, true);
    }

    public void markNotTicking() {
        regionizer.markNotTicking(this);
    }

    /** Hands over the sections whose chunks changed since the region last read them. A change that lands meanwhile marks its section again. */
    public void takeChanged(LongConsumer section) {
        for (RegionSection<R> next = changed.poll(); next != null; next = changed.poll()) {
            next.readAgain();
            if (next.region() == this) {
                section.accept(next.key());
            }
        }
    }

    public int sectionCount() {
        return regionizer.sectionCountOf(this);
    }

    // Used by the Leafs Debug mod
    public int chunkCount() {
        return regionizer.chunkCountOf(this);
    }

    public int deadSectionCount() {
        return regionizer.deadSectionCountOf(this);
    }

    // Used by the Leafs Debug mod
    public long[] sectionKeySnapshot() {
        return regionizer.sectionKeysOf(this);
    }

    void setState(RegionState state) {
        this.state = state;
        this.tickingThread = state == RegionState.TICKING ? Thread.currentThread() : null;
    }

    @Override
    public String toString() {
        Thread ticker = tickingThread;
        return "Region[#%s %s sections=%s%s]".formatted(id, state, sectionKeys.size(), ticker == null ? "" : " on thread '%s'".formatted(ticker.getName()));
    }
}
