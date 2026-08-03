package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.Regionizer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One per {@code ServerLevel}: owns the level's regionizer, follows its lifecycle and drives the tick
 * handshake once per level tick. The chunk-holder feed ({@link #chunkHolderCreated} / {@link
 * #chunkHolderDestroyed}) is the ONLY mutator of the regionizer - everything else reads it.
 *
 * <p>There is no per-region payload at M11a: {@code createData} returns {@code null}, so
 * {@link Region#data()} is null on every region of this level and must never be read. The payload
 * arrives with the first module that actually owns per-region state under the free-running pool.
 *
 * <p>RegionCallbacks implementations may touch only Leafs-owned in-memory state - never add or remove
 * a ticket, never call {@code ServerChunkCache}, never call back into the regionizer. The feed runs
 * inside {@code DynamicGraphMinFixedPoint.runUpdates}, so a callback adding a ticket would re-enter
 * the tracker queue being iterated, and the regionizer write lock throws on re-entry.
 */
public final class LevelRegions implements RegionCallbacks<Void> {
    private final Regionizer<Void> regionizer;

    /** Written only from the callbacks, which run under the regionizer write lock - hence plain increments. */
    private volatile long created;
    private volatile long merged;
    private volatile long split;
    private volatile int deferredHandshakes;
    private volatile Throwable feedFailure;

    public LevelRegions(LeafsConfig config) {
        this.regionizer = new Regionizer<>(config.gridSectionShift(), config.mergeRadius(), config.bufferRadius(), this);
    }

    public Regionizer<Void> regionizer() {
        return regionizer;
    }

    /** A chunk holder now exists at this position: the ticket level dropped to at most {@code ChunkLevel.MAX_LEVEL}. */
    public void chunkHolderCreated(int chunkX, int chunkZ) {
        try {
            regionizer.addChunk(chunkX, chunkZ);
        } catch (RuntimeException exception) {
            throw recordFeedFailure("create", chunkX, chunkZ, exception);
        }
    }

    public void chunkHolderDestroyed(int chunkX, int chunkZ) {
        try {
            regionizer.removeChunk(chunkX, chunkZ);
        } catch (RuntimeException exception) {
            throw recordFeedFailure("destroy", chunkX, chunkZ, exception);
        }
    }

    /**
     * The M11a tick handshake: every region is marked ticking and released with an empty body. It
     * touches zero game state and exists for one reason - {@code releaseFromTicking} is the only path
     * that splits regions, destroys emptied ones and reclaims dead sections, so without it an
     * exploration trail welds the world into one region that never shrinks again.
     */
    public void settle() {
        rethrowRecordedFailure();

        int deferred = 0;
        for (Region<Void> region : regionizer.regionsView()) {
            if (region.tryMarkTicking()) {
                region.markNotTicking();
            } else if (region.state() != RegionState.DEAD) {
                deferred++;
            }
        }

        deferredHandshakes = deferred;
    }

    /** Owner-thread census input: the chunks the regionizer believes this level holds. */
    public int trackedChunks() {
        return sumOverRegions(Region::chunkCount);
    }

    public int sections() {
        return sumOverRegions(Region::sectionCount);
    }

    public int deadSections() {
        return sumOverRegions(Region::deadSectionCount);
    }

    public long created() {
        return created;
    }

    public long merged() {
        return merged;
    }

    public long split() {
        return split;
    }

    /** Regions that could not complete the handshake this tick, i.e. blocked by a merge that is still pending. */
    public int deferredHandshakes() {
        return deferredHandshakes;
    }

    @Override
    public Void createData(Region<Void> region) {
        return null;
    }

    @Override
    public void onRegionCreate(Region<Void> region) {
        created++;
    }

    @Override
    public void onRegionDestroy(Region<Void> region) {
    }

    @Override
    public void onRegionActive(Region<Void> region) {
    }

    @Override
    public void onRegionInactive(Region<Void> region) {
    }

    @Override
    public void merge(Region<Void> from, Region<Void> into) {
        merged++;
    }

    @Override
    public void split(Region<Void> parent, Long2ObjectMap<Region<Void>> sectionToChild, List<Region<Void>> children) {
        split++;
    }

    private int sumOverRegions(ToIntFunction<Region<Void>> value) {
        int total = 0;
        for (Region<Void> region : regionizer.regionsView()) {
            total += value.applyAsInt(region);
        }

        return total;
    }

    /**
     * The feed also runs from threads that swallow what they are handed - {@code
     * ServerChunkCache.getChunkFuture} drops an off-thread failure silently - so the first failure is
     * kept and rethrown by the next {@link #settle()}, on the thread that owns the level tick.
     */
    private RuntimeException recordFeedFailure(String operation, int chunkX, int chunkZ, RuntimeException exception) {
        Leafs.LOGGER.error("Leafs region feed failed to {} chunk [{}, {}]", operation, chunkX, chunkZ, exception);
        if (feedFailure == null) {
            feedFailure = exception;
        }

        return exception;
    }

    private void rethrowRecordedFailure() {
        Throwable failure = feedFailure;
        if (failure == null) {
            return;
        }

        feedFailure = null;

        throw new IllegalStateException("Region feed failed earlier on this level", failure);
    }
}
