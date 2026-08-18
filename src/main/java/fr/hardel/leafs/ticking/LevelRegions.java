package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.RegionUnloads;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * One per {@code ServerLevel}, owning the regionizer and the region tick handle lifecycle. Callback
 * methods may never add or remove a ticket or call back into the regionizer: the feed already holds its write lock, and re-entering it throws.
 */
public final class LevelRegions implements RegionCallbacks<RegionTickData> {
    /** Mutated only by {@link #chunkHolderCreated} / {@link #chunkHolderDestroyed}; every other method just reads it. */
    private final Regionizer<RegionTickData> regionizer;
    private final LevelOwnership ownership = new LevelOwnership();
    private final RegionUnloads<RegionTickData> unloads = new RegionUnloads<>();

    private volatile String dimension;
    private volatile SharedChunkHolds holds;
    private volatile Consumer<Runnable> serialUnloadSink;
    private volatile RegionScheduler<RegionTickData> taskScheduler;
    private volatile Supplier<RegionWorldData> worldDataFactory;
    private volatile RegionTickBody body;
    private volatile RegionTickScheduler scheduler;

    /** Bumped by the global autosave trigger only; each region compares it against the epoch it last walked. */
    private volatile long autosaveEpoch;

    /** Written only from the callbacks, which run under the regionizer write lock - hence plain increments. */
    private volatile long created;
    private volatile long merged;
    private volatile long split;
    private volatile int deferredHandshakes;
    private volatile Throwable feedFailure;

    public LevelRegions(LeafsConfig config) {
        this.regionizer = new Regionizer<>(config.sectionShift(), config.regionMergeDistance(), config.regionBufferDistance(), this);
    }

    public static LevelRegions of(ServerLevel level) {
        return ((ServerLevelRegionAccess) level).leafs$regions();
    }

    public Regionizer<RegionTickData> regionizer() {
        return regionizer;
    }

    public LevelOwnership ownership() {
        return ownership;
    }

    /** Null until the level activates; region contexts only exist after activation. */
    public String dimensionName() {
        return dimension;
    }

    /**
     * Runs once on the server thread, under the level exclusion, before the level's first tick.
     * Regions are equipped first, the caller's migration then re-buckets the attached payloads, and
     * only then are the handles scheduled, so no region can tick against a half-migrated level.
     */
    public void activate(String dimension, RegionTickScheduler scheduler, SharedChunkHolds holds, RegionScheduler<RegionTickData> taskScheduler, Consumer<Runnable> serialUnloadSink, Supplier<RegionWorldData> worldDataFactory, RegionTickBody body, Runnable beforeScheduling) {
        if (this.scheduler != null)
            return;

        this.dimension = dimension;
        this.holds = holds;
        this.serialUnloadSink = serialUnloadSink;
        this.taskScheduler = taskScheduler;
        this.worldDataFactory = worldDataFactory;
        this.body = body;

        for (Region<RegionTickData> region : regionizer.regionsView())
            if (region.data().worldData() == null)
                region.data().attachWorldData(worldDataFactory.get());

        beforeScheduling.run();
        this.scheduler = scheduler;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.data().handle() == null && (region.state() == RegionState.READY || region.state() == RegionState.TICKING)) {
                region.data().attachHandle(new RegionTickHandle(region, dimension, this));
                scheduler.schedule(region.data().handle());
            }
        }
    }

    public RegionTickBody body() {
        return body;
    }

    public SharedChunkHolds holds() {
        return holds;
    }

    public RegionScheduler<RegionTickData> taskScheduler() {
        return taskScheduler;
    }

    public RegionUnloads<RegionTickData> unloads() {
        return unloads;
    }

    public void bumpAutosaveEpoch() {
        autosaveEpoch++;
    }

    public long autosaveEpoch() {
        return autosaveEpoch;
    }

    /** Universal-owner drain: the shutdown path and the exclusion-held wait loops run every queued region task inline. */
    public int drainTasksInline() {
        RegionScheduler<RegionTickData> scheduler = taskScheduler;
        if (scheduler == null) {
            return 0;
        }

        int drained = scheduler.drainPendingInline();
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            drained += scheduler.drain(region);
        }

        return drained;
    }

    /** Shutdown path, pool already stopped: every queued teardown runs inline so the final save misses nothing. */
    public void drainUnloadsForShutdown() {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            region.data().unloadQueues().closeDraining(Runnable::run);
        }
    }

    /** A chunk holder now exists at this position: the ticket level dropped to at most {@code ChunkLevel.MAX_LEVEL}. */
    public void chunkHolderCreated(int chunkX, int chunkZ) {
        try {
            regionizer.addChunk(chunkX, chunkZ);
        } catch (RuntimeException exception) {
            throw recordFeedFailure("create", chunkX, chunkZ, exception);
        }
    }

    /** The owner is captured before the removal, because after it the chunk belongs to nobody (see {@link RegionUnloads}). */
    public void chunkHolderDestroyed(int chunkX, int chunkZ) {
        try {
            unloads.noteOwner(ChunkPos.pack(chunkX, chunkZ), regionizer.regionAt(chunkX, chunkZ));
            regionizer.removeChunk(chunkX, chunkZ);
        } catch (RuntimeException exception) {
            throw recordFeedFailure("destroy", chunkX, chunkZ, exception);
        }
    }

    /**
     * Marks every region ticking then releases it with an empty body, which is what triggers splits,
     * destroys and reclaims. Regions normally handshake through their own tick handle; this covers the shutdown drain and levels whose pool never bound.
     */
    public void settle() {
        rethrowFeedFailure();

        int deferred = 0;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.tryMarkTicking()) {
                region.markNotTicking();
            } else if (region.state() != RegionState.DEAD) {
                deferred++;
            }
        }

        deferredHandshakes = deferred;
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
    public RegionTickData createData(Region<RegionTickData> region) {
        RegionTickData data = new RegionTickData();
        data.attachEntityData(new RegionEntityData());
        Supplier<RegionWorldData> factory = worldDataFactory;
        if (factory != null) {
            data.attachWorldData(factory.get());
        }

        if (scheduler != null) {
            data.attachHandle(new RegionTickHandle(region, dimension, this));
        }

        return data;
    }

    @Override
    public void onRegionCreate(Region<RegionTickData> region) {
        created++;
    }

    @Override
    public void onRegionDestroy(Region<RegionTickData> region) {
        Consumer<Runnable> sink = serialUnloadSink;
        if (sink != null) {
            region.data().unloadQueues().closeDraining(task -> sink.accept(new OrphanedUnload(task)));
        }
    }

    /** Names the serial-fallback teardown of a dead region's chunk, so the slow-task tracer attributes it. */
    private record OrphanedUnload(Runnable task) implements Runnable {
        @Override
        public void run() {
            task.run();
        }
    }

    @Override
    public void onRegionActive(Region<RegionTickData> region) {
        RegionTickHandle handle = region.data().handle();
        if (handle != null) {
            scheduler.schedule(handle);
        }
    }

    @Override
    public void onRegionInactive(Region<RegionTickData> region) {
        RegionTickHandle handle = region.data().handle();
        if (handle != null) {
            handle.cancel();
        }
    }

    @Override
    public void merge(Region<RegionTickData> from, Region<RegionTickData> into) {
        from.data().taskQueues().closeInto(into.data().taskQueues());
        from.data().unloadQueues().closeInto(into.data().unloadQueues());
        RegionWorldData fromWorld = from.data().worldData();
        RegionWorldData intoWorld = into.data().worldData();
        if (fromWorld != null && intoWorld != null) {
            fromWorld.mergeInto(intoWorld);
        }

        from.data().entityData().mergeInto(into.data().entityData());
        into.data().autosave().absorb(from.data().autosave());
        merged++;
    }

    @Override
    public void split(Region<RegionTickData> parent, Long2ObjectMap<Region<RegionTickData>> sectionToChild, List<Region<RegionTickData>> children) {
        parent.data().taskQueues().closeAndReroute(regionizer.sectionShift(), sectionKey -> {
            Region<RegionTickData> child = sectionToChild.get(sectionKey);
            return child == null ? null : child.data().taskQueues();
        });

        parent.data().unloadQueues().closeAndReroute(regionizer.sectionShift(), sectionKey -> {
            Region<RegionTickData> child = sectionToChild.get(sectionKey);
            return child == null ? null : child.data().unloadQueues();
        });

        RegionWorldData parentWorld = parent.data().worldData();
        if (parentWorld != null) {
            for (Region<RegionTickData> child : children) {
                child.data().worldData().inheritTimeFrom(parentWorld);
            }

            parentWorld.splitInto(regionizer.sectionShift(), sectionKey -> {
                Region<RegionTickData> child = sectionToChild.get(sectionKey);
                return child == null ? null : child.data().worldData();
            });
        }

        parent.data().entityData().splitInto(regionizer.sectionShift(), sectionKey -> {
            Region<RegionTickData> child = sectionToChild.get(sectionKey);
            return child == null ? null : child.data().entityData();
        });

        for (Region<RegionTickData> child : children) {
            child.data().autosave().inheritFrom(parent.data().autosave());
        }

        split++;
    }

    private int sumOverRegions(ToIntFunction<Region<RegionTickData>> value) {
        int total = 0;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            total += value.applyAsInt(region);
        }

        return total;
    }

    /**
     * The feed can run on a thread that swallows exceptions ({@code ServerChunkCache.getChunkFuture}
     * drops them silently), so the first failure is kept here and rethrown later by quiesce or settle, on a thread that owns the level.
     */
    private RuntimeException recordFeedFailure(String operation, int chunkX, int chunkZ, RuntimeException exception) {
        Leafs.LOGGER.error("Leafs region feed failed to {} chunk [{}, {}]", operation, chunkX, chunkZ, exception);
        if (feedFailure == null) {
            feedFailure = exception;
        }

        return exception;
    }

    public void rethrowFeedFailure() {
        Throwable failure = feedFailure;
        if (failure == null) {
            return;
        }

        feedFailure = null;
        throw new IllegalStateException("Region feed failed earlier on this level", failure);
    }
}
