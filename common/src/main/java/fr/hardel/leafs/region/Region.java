package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Region<R> {
    private final long id;
    private final Regionizer<R> regionizer;
    private final R data;

    final LongOpenHashSet sectionKeys = new LongOpenHashSet();
    final LongOpenHashSet deadSectionKeys = new LongOpenHashSet();
    final Set<Region<R>> mergeIntoLater = new LinkedHashSet<>();
    final Set<Region<R>> expectingMergeFrom = new LinkedHashSet<>();

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

    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    public boolean owns(int chunkX, int chunkZ) {
        return regionizer.regionAtUnsynchronised(chunkX, chunkZ) == this;
    }

    public void forEachChunk(ChunkConsumer consumer) {
        regionizer.forEachChunkOf(this, consumer);
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
        return "Region[#" + id + " " + state + " sections=" + sectionKeys.size() + (ticker == null ? "" : " on thread '" + ticker.getName() + "'") + "]";
    }
}
