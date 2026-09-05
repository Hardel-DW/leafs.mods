package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.SavedEpochAccess;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.world.ChunkScheduledTicks;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/** One per level, owns the regionizer and the handle lifecycle. Callbacks run under the regionizer's write lock: no tickets, no lookup, no re-entry. */
public final class LevelRegions implements RegionCallbacks<RegionTickData>, LevelListener {
    /** Mutated only by the simulation graph; every other method just reads it. */
    private final Regionizer<RegionTickData> regionizer;
    private volatile String dimension;
    private volatile LongSupplier gameTime;
    private volatile Function<LongSupplier, RegionWorldData> worldDataFactory;
    private volatile RegionTickBody body;
    private volatile RegionTickScheduler scheduler;
    /** Bumped by the global autosave trigger only; each chunk and player compares it against the epoch that last saved it. A forced epoch saves everything in one pass. */
    private volatile long autosaveEpoch;
    private volatile boolean autosaveForced;
    /** Written only from the callbacks, which run under the regionizer write lock, hence plain increments. */
    private volatile long created;
    private volatile long destroyed;
    private volatile long merged;
    private volatile long split;
    /** Totals of the handles that are gone; a sampler adds the live ones to get the level's work, whatever the churn did in between. */
    private volatile long retiredBusyNanos;
    private volatile long retiredLagNanos;
    private volatile long retiredTicks;
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

    /** Once, on the server thread, before the level's first tick. Regions equip, then the handles schedule. */
    public void activate(String dimension, RegionTickScheduler scheduler, LongSupplier gameTime, Function<LongSupplier, RegionWorldData> worldDataFactory, RegionTickBody body) {
        if (this.scheduler != null) {
            return;
        }

        this.dimension = dimension;
        this.gameTime = gameTime;
        this.worldDataFactory = worldDataFactory;
        this.body = body;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.data().worldData() == null) {
                equipWorld(region.data());
            }
        }

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

    /** Regions tick between activation and the halt; outside that window the server thread owns every chunk. */
    public boolean live() {
        RegionTickBody body = this.body;
        return body != null && !TickingManager.of(body.level().getServer()).halted();
    }

    /** The level, once activated. */
    public ServerLevel level() {
        return body.level();
    }

    /** The inbox of the region covering a chunk, null without one or while the regions do not run. */
    public @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        if (!live()) {
            return null;
        }

        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.data().inbox();
    }

    /** Every region's inbox, for the thread that owns them all. */
    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            drained += region.data().inbox().drain();
        }

        return drained;
    }

    /** The owning region's payload, null for a chunk without a region or before activation. */
    public RegionWorldData worldDataAt(int chunkX, int chunkZ) {
        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.data().worldData();
    }

    /** The clock a scheduled tick at this chunk lives on: the owning region's, or game time when nobody owns it. */
    public long timeAt(int chunkX, int chunkZ, long gameTime) {
        RegionWorldData data = worldDataAt(chunkX, chunkZ);
        return data == null ? gameTime : data.currentTick();
    }

    public void bumpAutosaveEpoch(boolean forced) {
        autosaveForced = forced;
        autosaveEpoch++;
    }

    public long autosaveEpoch() {
        return autosaveEpoch;
    }

    public boolean autosaveForced() {
        return autosaveForced;
    }

    /** Whether every loaded chunk and every player saved the epoch: each belongs to a region or to the pool, so each gets there. */
    public boolean autosaveReached(long epoch, Iterable<ChunkHolder> holders, List<ServerPlayer> players) {
        for (ChunkHolder holder : holders) {
            if (((SavedEpochAccess) holder).leafs$savedEpoch() < epoch) {
                return false;
            }
        }

        for (ServerPlayer player : players) {
            if (((SavedEpochAccess) player).leafs$savedEpoch() < epoch) {
                return false;
            }
        }

        return true;
    }

    /** The simulation graph: a chunk entering or leaving block ticking is what shapes the regions. */
    @Override
    public void changed(long chunkKey, int oldLevel, int newLevel) {
        boolean simulates = ChunkLevel.isBlockTicking(newLevel);
        if (simulates == ChunkLevel.isBlockTicking(oldLevel)) {
            return;
        }

        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        try {
            if (simulates) {
                regionizer.addChunk(chunkX, chunkZ);
            } else {
                regionizer.removeChunk(chunkX, chunkZ);
            }
        } catch (RuntimeException exception) {
            throw recordFeedFailure(simulates ? "simulate" : "unsimulate", chunkX, chunkZ, exception);
        }
    }

    /** Every region handle cancelled: the level is closing, nothing of it ticks again. */
    public void retire() {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null) {
                handle.cancel();
            }
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

    public long destroyed() {
        return destroyed;
    }

    public long retiredBusyNanos() {
        return retiredBusyNanos;
    }

    public long retiredLagNanos() {
        return retiredLagNanos;
    }

    public long retiredTicks() {
        return retiredTicks;
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

        int players = disconnectPlayers(region);
        keepChunksAsSaved(region);
        equipWorld(data);
        scheduler.schedule(newHandle(region));
        Leafs.LOGGER.warn("Region #{} in {} restarted after a crash, {} players disconnected", region.id(), dimension, players);
        return true;
    }

    private int disconnectPlayers(Region<RegionTickData> region) {
        Component reason = Component.literal("Your region crashed, please reconnect");
        int count = 0;
        for (ServerPlayer player : body.level().players()) {
            ChunkPos chunk = player.chunkPosition();
            if (region.owns(chunk.x(), chunk.z())) {
                player.connection.disconnect(reason);
                count++;
            }
        }

        return count;
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
            retire(previous);
        }

        RegionTickHandle handle = new RegionTickHandle(region, dimension, this);
        region.data().attachHandle(handle);
        return handle;
    }

    /** Called wherever a handle stops being used, a crash restart or the region's death, so no work goes missing. */
    private void retire(RegionTickHandle handle) {
        StageTimings stages = handle.stages();
        retiredBusyNanos += stages.busyNanos();
        retiredLagNanos += stages.lagNanos();
        retiredTicks += stages.completedTicks();
    }

    @Override
    public void onRegionCreate(Region<RegionTickData> region) {
        created++;
    }

    /** A dead region's inbox goes back through the owners, off this lock. */
    @Override
    public void onRegionDestroy(Region<RegionTickData> region) {
        destroyed++;
        RegionTickHandle handle = region.data().handle();
        if (handle != null) {
            retire(handle);
        }

        if (body != null) {
            LevelChunks.of(body.level()).owners().abandon(region.data().inbox());
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

    /** The moved chunks carry their scheduled ticks onto the survivor's clock, and the dead region's inbox pours into the survivor's. */
    @Override
    public void merge(Region<RegionTickData> from, Region<RegionTickData> into, LongList movedChunks) {
        RegionTickBody body = this.body;
        if (body != null) {
            rebaseTicks(body.level(), movedChunks, into.data().clock().currentTick() - from.data().clock().currentTick());
        }

        RegionInbox survivor = into.data().inbox();
        from.data().inbox().close(posted -> survivor.post(posted.chunkX(), posted.chunkZ(), posted.task()));
        merged++;
    }

    private static void rebaseTicks(ServerLevel level, LongList chunks, long tickOffset) {
        if (tickOffset == 0) {
            return;
        }

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (long key : chunks) {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(CoordinateKey.x(key), CoordinateKey.z(key)));
            if (holder != null && holder.getLatestChunk() instanceof LevelChunk chunk) {
                ChunkScheduledTicks.rebase(chunk, tickOffset);
            }
        }
    }

    /** Children start on the parent's clock once regions are equipped; each posted task follows its section to its child. */
    @Override
    public void split(Region<RegionTickData> parent, Long2ObjectMap<Region<RegionTickData>> sectionToChild, List<Region<RegionTickData>> children) {
        if (worldDataFactory != null) {
            for (Region<RegionTickData> child : children) {
                child.data().clock().resetTo(parent.data().clock().currentTick());
            }
        }

        int shift = regionizer.sectionShift();
        parent.data().inbox().close(posted -> {
            Region<RegionTickData> child = sectionToChild.get(CoordinateKey.pack(posted.chunkX() >> shift, posted.chunkZ() >> shift));
            child.data().inbox().post(posted.chunkX(), posted.chunkZ(), posted.task());
        });
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
