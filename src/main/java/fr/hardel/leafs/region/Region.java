package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.LinkedHashSet;
import java.util.Set;

/** Nearby ticking sections and their ring, owned as one unit, guarded by the regionizer's write lock. While TICKING no section leaves and merges wait. */
public final class Region<R> {
    private final long id;
    private final Regionizer<R> regionizer;
    private final R data;

    final LongOpenHashSet sectionKeys = new LongOpenHashSet();
    final LongOpenHashSet deadSectionKeys = new LongOpenHashSet();
    final Set<Region<R>> mergeIntoLater = new LinkedHashSet<>();
    final Set<Region<R>> expectingMergeFrom = new LinkedHashSet<>();

    private volatile RegionState state = RegionState.READY;

    Region(long id, Regionizer<R> regionizer, RegionCallbacks<R> callbacks) {
        this.id = id;
        this.regionizer = regionizer;
        this.data = callbacks.createData(this);
    }

    public long id() {
        return id;
    }

    public R data() {
        return data;
    }

    public RegionState state() {
        return state;
    }

    /** Only when idle with no pending merge; an awaited merge partner never ticks on its own. */
    public boolean tryMarkTicking() {
        return regionizer.tryMarkTicking(this);
    }

    public void markNotTicking() {
        regionizer.markNotTicking(this);
    }

    public int sectionCount() {
        return regionizer.sectionCountOf(this);
    }

    /** The chunks that tick, not the ring around them. */
    public int chunkCount() {
        return regionizer.chunkCountOf(this);
    }

    /** Sections kept only until the owner is released: reclaim is deliberately lazy, see {@code releaseFromTicking}. */
    public int deadSectionCount() {
        return regionizer.deadSectionCountOf(this);
    }

    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    /** Ticking or in the ring alike; lock-free, the owner's own view of its chunks cannot change under it. */
    public boolean owns(int chunkX, int chunkZ) {
        return regionizer.regionAtUnsynchronised(chunkX, chunkZ) == this;
    }

    /** Every owned position, ticking or in the ring, loaded or not; a snapshot of the sections, so the owner may call it mid-tick while the feed adopts more. */
    public void forEachChunk(ChunkConsumer consumer) {
        regionizer.forEachChunkOf(this, consumer);
    }

    /** Copy of the section keys, safe to walk from any thread while the live set keeps moving. */
    public long[] sectionKeySnapshot() {
        return regionizer.sectionKeysOf(this);
    }

    public long[] tickingSectionKeySnapshot() {
        return regionizer.tickingSectionKeysOf(this);
    }

    void setState(RegionState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "Region[#" + id + " " + state + " sections=" + sectionKeys.size() + "]";
    }
}
