package fr.hardel.leafs.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class LevelRegions implements RegionCallbacks<RegionTickData>, LevelListener, ChunkOwners.Regions {
    private final Regionizer<RegionTickData> regionizer;
    private volatile String dimension;
    private volatile LongSupplier nanosPerTick;
    private volatile Supplier<RegionWorldData> worldDataFactory;
    private volatile RegionTickBody body;
    private volatile RegionTickScheduler scheduler;
    private volatile long autosaveEpoch;
    private volatile long created;
    private volatile long destroyed;
    private volatile long merged;
    private volatile long split;

    public LevelRegions(LeafsConfig config) {
        this.regionizer = new Regionizer<>(config.sectionShift(), config.regionMergeDistance(), config.regionBufferDistance(), this);
    }

    @Override
    public void chunkChanged(int chunkX, int chunkZ) {
        regionizer.markChanged(chunkX, chunkZ);
    }

    // Used by the Leafs Debug mod
    public static LevelRegions of(ServerLevel level) {
        return ((ServerLevelRegionAccess) level).leafs$regions();
    }

    // Used by the Leafs Debug mod
    public Regionizer<RegionTickData> regionizer() {
        return regionizer;
    }

    public void activate(String dimension, RegionTickScheduler scheduler, LongSupplier nanosPerTick, Supplier<RegionWorldData> worldDataFactory, RegionTickBody body) {
        if (this.scheduler != null) {
            return;
        }

        this.dimension = dimension;
        this.nanosPerTick = nanosPerTick;
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

    @Override
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

    @Override
    public @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        if (!live()) {
            return null;
        }

        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.data().inbox();
    }

    @Override
    public @Nullable Thread tickerAt(int chunkX, int chunkZ) {
        Region<RegionTickData> region = regionizer.regionAt(chunkX, chunkZ);
        return region == null ? null : region.tickingThread();
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

        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        if (simulates) {
            regionizer.addChunk(chunkX, chunkZ);
        } else {
            regionizer.removeChunk(chunkX, chunkZ);
        }

        regionizer.markChanged(chunkX, chunkZ);
    }

    public void retire() {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null) {
                handle.cancel();
            }
        }
    }

    public boolean all(Predicate<RegionTime> test) {
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            RegionWorldData worldData = region.data().worldData();
            if (worldData != null && !test.test(worldData.time())) {
                return false;
            }
        }

        return true;
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
        RegionTickData data = new RegionTickData(this::owners);
        if (worldDataFactory != null) {
            equipWorld(data);
        }

        if (scheduler != null) {
            data.attachHandle(new RegionTickHandle(region, dimension, this, nanosPerTick));
        }

        return data;
    }

    private void equipWorld(RegionTickData data) {
        data.equipWorld(worldDataFactory.get());
    }

    private RegionTickHandle newHandle(Region<RegionTickData> region) {
        RegionTickHandle handle = new RegionTickHandle(region, dimension, this, nanosPerTick);
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
            scheduler.schedule(handle.isCancelled() ? newHandle(region) : handle);
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
        if (body != null) {
            into.data().worldData().forgetEpoch();
        }

        RegionInbox survivor = into.data().inbox();
        from.data().inbox().close(posted -> survivor.post(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task()));
        merged++;
    }

    @Override
    public void released(Region<RegionTickData> region, long sectionKey) {
        RegionWorldData worldData = region.data().worldData();
        if (worldData != null) {
            worldData.chunks().forget(sectionKey);
        }
    }

    @Override
    public void split(Region<RegionTickData> parent, Long2ObjectMap<Region<RegionTickData>> sectionToChild, List<Region<RegionTickData>> children) {
        if (worldDataFactory != null) {
            for (Region<RegionTickData> child : children) {
                child.data().worldData().time().inherit(parent.data().worldData().time());
            }
        }

        int shift = regionizer.sectionShift();
        RegionInbox orphans = new RegionInbox();
        parent.data().inbox().close(posted -> {
            Region<RegionTickData> child = sectionToChild.get(ChunkPos.pack(posted.chunkX() >> shift, posted.chunkZ() >> shift));
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
