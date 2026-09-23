package fr.hardel.leafs.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.RegionInbox;
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
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

public final class LevelRegions implements RegionCallbacks<RegionTickData>, LevelListener {
    private final Regionizer<RegionTickData> regionizer;
    private volatile String dimension;
    private volatile LongSupplier gameTime;
    private volatile Function<LongSupplier, RegionWorldData> worldDataFactory;
    private volatile RegionTickBody body;
    private volatile RegionTickScheduler scheduler;
    private volatile long autosaveEpoch;
    private volatile long created;
    private volatile long destroyed;
    private volatile long merged;
    private volatile long split;
    private final long slowTaskNanos;

    public LevelRegions(LeafsConfig config) {
        this.regionizer = new Regionizer<>(config.sectionShift(), config.regionMergeDistance(), config.regionBufferDistance(), this);
        this.slowTaskNanos = config.debug().slowTaskNanos();
    }

    public long slowTaskNanos() {
        return slowTaskNanos;
    }

    // Used by the Leafs Debug mod
    public static LevelRegions of(ServerLevel level) {
        return ((ServerLevelRegionAccess) level).leafs$regions();
    }

    // Used by the Leafs Debug mod
    public Regionizer<RegionTickData> regionizer() {
        return regionizer;
    }

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

    public boolean live() {
        RegionTickBody body = this.body;
        return body != null && !TickingManager.of(body.level().getServer()).halted();
    }

    public ServerLevel level() {
        return body.level();
    }

    private ChunkOwners owners() {
        return LevelChunks.of(body.level()).owners();
    }

    public @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        if (!live()) {
            return null;
        }

        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.data().inbox();
    }

    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            drained += region.data().inbox().drain();
        }

        return drained;
    }

    public RegionWorldData worldDataAt(int chunkX, int chunkZ) {
        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.data().worldData();
    }

    public long timeAt(int chunkX, int chunkZ, long gameTime) {
        RegionWorldData data = worldDataAt(chunkX, chunkZ);
        return data == null ? gameTime : data.currentTick();
    }

    public void bumpAutosaveEpoch() {
        autosaveEpoch++;
    }

    public long autosaveEpoch() {
        return autosaveEpoch;
    }

    @Override
    public void changed(long chunkKey, int oldLevel, int newLevel) {
        boolean simulates = ChunkLevel.isBlockTicking(newLevel);
        if (simulates == ChunkLevel.isBlockTicking(oldLevel)) {
            return;
        }

        if (simulates) {
            regionizer.addChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        } else {
            regionizer.removeChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        }
    }

    public void retire() {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null) {
                handle.cancel();
            }
        }
    }

    public void settle() {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            if (region.tryMarkTicking()) {
                region.markNotTicking();
            }
        }
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

    public long merged() {
        return merged;
    }

    public long split() {
        return split;
    }

    @Override
    public RegionTickData createData(Region<RegionTickData> region) {
        RegionTickData data = new RegionTickData(region, slowTaskNanos, this::owners);
        if (worldDataFactory != null) {
            equipWorld(data);
        }

        if (scheduler != null) {
            data.attachHandle(new RegionTickHandle(region, dimension, this));
        }

        return data;
    }

    private void equipWorld(RegionTickData data) {
        RegionClock clock = new RegionClock(gameTime.getAsLong());
        data.equipWorld(clock, worldDataFactory.apply(clock::currentTick));
    }

    private RegionTickHandle newHandle(Region<RegionTickData> region) {
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
        destroyed++;
        if (body != null) {
            owners().abandon(region.data().inbox());
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
    public void merge(Region<RegionTickData> from, Region<RegionTickData> into, LongList movedChunks) {
        RegionTickBody body = this.body;
        if (body != null) {
            rebaseTicks(body.level(), movedChunks, into.data().clock().currentTick() - from.data().clock().currentTick());
            into.data().worldData().forgetEpoch();
        }

        RegionInbox survivor = into.data().inbox();
        from.data().inbox().close(posted -> survivor.post(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task()));
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

    @Override
    public void split(Region<RegionTickData> parent, Long2ObjectMap<Region<RegionTickData>> sectionToChild, List<Region<RegionTickData>> children) {
        if (worldDataFactory != null) {
            for (Region<RegionTickData> child : children) {
                child.data().clock().resetTo(parent.data().clock().currentTick());
            }
        }

        int shift = regionizer.sectionShift();
        RegionInbox orphans = new RegionInbox(slowTaskNanos);
        parent.data().inbox().close(posted -> {
            Region<RegionTickData> child = sectionToChild.get(CoordinateKey.pack(posted.chunkX() >> shift, posted.chunkZ() >> shift));
            RegionInbox target = child == null ? orphans : child.data().inbox();
            target.post(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task());
        });
        
        if (body != null && orphans.size() > 0) {
            owners().abandon(orphans);
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
}
