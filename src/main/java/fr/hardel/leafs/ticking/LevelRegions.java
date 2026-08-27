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
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/** One per level, owns the regionizer and the handle lifecycle. Callbacks run under the regionizer's write lock: no tickets, no re-entry. */
public final class LevelRegions implements RegionCallbacks<RegionTickData> {
    /** Mutated only by {@link #chunkHolderCreated} / {@link #chunkHolderDestroyed}; every other method just reads it. */
    private final Regionizer<RegionTickData> regionizer;
    private final RegionUnloads<RegionTickData> unloads = new RegionUnloads<>();

    private volatile String dimension;
    private volatile Consumer<Runnable> serialUnloadSink;
    private volatile RegionScheduler<RegionTickData> taskScheduler;
    private volatile LongSupplier gameTime;
    private volatile Function<LongSupplier, RegionWorldData> worldDataFactory;
    private volatile RegionTickBody body;
    private volatile RegionTickScheduler scheduler;

    /** Bumped by the global autosave trigger only; each region compares it against the epoch it last walked. */
    private volatile long autosaveEpoch;

    /** Written only from the callbacks, which run under the regionizer write lock, hence plain increments. */
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

    /** Null until the level activates; region contexts only exist after activation. */
    public String dimensionName() {
        return dimension;
    }

    /** Once, on the server thread, before the level's first tick. Regions equip, the payloads migrate, then the handles schedule. */
    public void activate(String dimension, RegionTickScheduler scheduler, RegionScheduler<RegionTickData> taskScheduler, Consumer<Runnable> serialUnloadSink, LongSupplier gameTime, Function<LongSupplier, RegionWorldData> worldDataFactory, RegionTickBody body, Runnable beforeScheduling) {
        if (this.scheduler != null)
            return;

        this.dimension = dimension;
        this.serialUnloadSink = serialUnloadSink;
        this.taskScheduler = taskScheduler;
        this.gameTime = gameTime;
        this.worldDataFactory = worldDataFactory;
        this.body = body;

        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.data().worldData() == null) {
                equipWorld(region.data());
            }
        }

        beforeScheduling.run();
        this.scheduler = scheduler;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.data().handle() == null && (region.state() == RegionState.READY || region.state() == RegionState.TICKING)) {
                scheduler.schedule(newHandle(region));
            }
        }
    }

    public RegionTickBody body() {
        return body;
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

    /** What the region lanes still owe: a shutdown that cannot finish needs to know whether the work waits on a lane nobody drains. */
    public int queuedWork() {
        int queued = 0;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            queued += region.data().taskQueues().size() + region.data().unloadQueues().size();
        }

        return queued;
    }

    /** Universal-owner drain, barrier held or pool stopped: nobody else can tick this level. */
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

    public void chunkHolderDestroyed(int chunkX, int chunkZ) {
        try {
            regionizer.removeChunk(chunkX, chunkZ);
        } catch (RuntimeException exception) {
            throw recordFeedFailure("destroy", chunkX, chunkZ, exception);
        }
    }

    /** An empty tick on every region triggers splits, destroys and reclaims; for the shutdown drain and levels whose pool never bound. */
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
        if (worldDataFactory != null) {
            equipWorld(data);
        }

        if (scheduler != null) {
            data.attachHandle(new RegionTickHandle(region, dimension, this));
        }

        return data;
    }

    /**
     * A crashed region: its players reconnect through the normal join, its chunks keep their in-memory
     * state but count as saved, its tick payload is dropped and it reschedules. A second death within the window is not recoverable.
     */
    boolean restart(Region<RegionTickData> region) {
        RegionTickData data = region.data();
        if (!data.recordDeath(System.nanoTime())) {
            return false;
        }

        int players = disconnectPlayers(data.entityData());
        keepChunksAsSaved(region);
        data.renewEntityData();
        equipWorld(data);
        scheduler.schedule(newHandle(region));
        Leafs.LOGGER.warn("Region #{} in {} restarted after a crash, {} players disconnected", region.id(), dimension, players);
        return true;
    }

    private int disconnectPlayers(RegionEntityData entityData) {
        Component reason = Component.literal("Your region crashed, please reconnect");
        int[] count = new int[1];
        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                player.connection.disconnect(reason);
                count[0]++;
            }
        });

        return count[0];
    }

    /** Only what changes after the crash reaches the disk; a restart of the server returns the area to its last save, as a vanilla crash would. */
    private void keepChunksAsSaved(Region<RegionTickData> region) {
        ChunkMap chunkMap = body.level().getChunkSource().chunkMap;
        region.forEachChunk((chunkX, chunkZ) -> {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
            ChunkAccess chunk = holder == null ? null : holder.getLatestChunk();
            if (chunk != null) {
                chunk.tryMarkSaved();
            }
        });
    }

    /** A clock starts at game time, so ticks unpacked before the region existed keep their delays. */
    private void equipWorld(RegionTickData data) {
        RegionClock clock = new RegionClock(gameTime.getAsLong());
        data.equipWorld(clock, worldDataFactory.apply(clock::currentTick));
    }

    /** The previous handle, if any, must never be requeued by the worker that ran it. */
    private RegionTickHandle newHandle(Region<RegionTickData> region) {
        RegionTickHandle previous = region.data().handle();
        if (previous != null) {
            previous.cancel();
        }

        RegionTickHandle handle = new RegionTickHandle(region, dimension, this);
        region.data().attachHandle(handle);
        return handle;
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
        Consumer<Runnable> orphans = task -> serialUnloadSink.accept(new OrphanedUnload(task));
        parent.data().taskQueues().closeAndReroute(regionizer.sectionShift(), sectionKey -> {
            Region<RegionTickData> child = sectionToChild.get(sectionKey);
            return child == null ? null : child.data().taskQueues();
        }, orphans);

        parent.data().unloadQueues().closeAndReroute(regionizer.sectionShift(), sectionKey -> {
            Region<RegionTickData> child = sectionToChild.get(sectionKey);
            return child == null ? null : child.data().unloadQueues();
        }, orphans);

        RegionWorldData parentWorld = parent.data().worldData();
        if (parentWorld != null) {
            for (Region<RegionTickData> child : children) {
                child.data().clock().resetTo(parent.data().clock().currentTick());
                child.data().worldData().inheritCountersFrom(parentWorld);
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

    /** The feed can run where exceptions are swallowed, so the first failure is kept and rethrown by quiesce or settle. */
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
