package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.ChunkTicketHolds;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.entity.LevelEntityLists;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.RegionCrashReport;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import fr.hardel.leafs.world.RegionScheduledTicks;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.RoutingScheduledTicks;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import fr.hardel.leafs.world.WorldDataRouter;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.LongFunction;

/** The server-thread remainder of the level tick. Activation flips routing, migrates the attached payloads and schedules the regions, before any region ticks. */
public final class LevelTickUnit extends TickHandle {
    private static final int CENSUS_INTERVAL_TICKS = 100;

    private final ServerLevel level;
    private final LevelRegions regions;
    private final RegionTickScheduler scheduler;
    private final SerialWorkBudget serialBudget;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Runnable pendingWork;
    private boolean activated;
    private volatile int lastChunkCount;
    private volatile int lastViewChunks;

    LevelTickUnit(long id, ServerLevel level, RegionTickScheduler scheduler, SerialWorkBudget serialBudget) {
        super(new RegionContext.LevelSerial(id, level.dimension().identifier().toString()), TickStages.count(TickFamily.SERIAL));
        this.level = level;
        this.regions = LevelRegions.of(level);
        this.scheduler = scheduler;
        this.serialBudget = serialBudget;
    }

    public LevelRegions regions() {
        return regions;
    }

    /** Server thread only, before the first tick of this level; no region is scheduled yet. */
    void ensureActivated() {
        if (activated) {
            return;
        }

        activated = true;
        RegionTickBody body = new RegionTickBody(level);
        Regionizer<RegionTickData> regionizer = regions.regionizer();
        int sectionShift = regionizer.sectionShift();
        WorldDataRouter router = ((ServerLevelWorldAccess) level).leafs$worldRouter();
        LevelEntityLists entityLists = ((ServerLevelEntityAccess) level).leafs$entityLists();
        SharedChunkHolds holds = new SharedChunkHolds(new ChunkTicketHolds(level));
        RegionScheduler<RegionTickData> taskScheduler = new RegionScheduler<>(regionizer, holds);
        LongFunction<RegionWorldData> regionWorldData = chunkKey -> resolve(regionizer, chunkKey, data -> data.worldData());
        LongFunction<RegionEntityData> regionEntityData = chunkKey -> resolve(regionizer, chunkKey, data -> data.entityData());
        regions.activate(dimension(), scheduler, taskScheduler, this::submit, level::getGameTime, time -> RegionWorldData.regional(level, time), body, () -> {
            router.route(chunkKey -> orAttached(regionWorldData.apply(chunkKey), router.attached()));
            entityLists.route(chunkKey -> orAttached(regionEntityData.apply(chunkKey), entityLists.attached()));
            routeScheduledTicks(router, regionizer);
            router.attached().migrateInto(sectionShift, sectionKey -> regionWorldData.apply(firstChunkOf(sectionKey, sectionShift)));
            entityLists.migrateAttached(sectionShift, sectionKey -> regionEntityData.apply(firstChunkOf(sectionKey, sectionShift)));
            body.migrateVanillaBlockEntityTickers(regionWorldData);
        });
    }

    private <T> T resolve(Regionizer<RegionTickData> regionizer, long chunkKey, Function<RegionTickData, T> part) {
        Region<RegionTickData> region = regionizer.regionAt(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));

        return region == null ? null : part.apply(region.data());
    }

    private static <T> T orAttached(T resolved, T attached) {
        return resolved == null ? attached : resolved;
    }

    private static long firstChunkOf(long sectionKey, int sectionShift) {
        return ChunkPos.pack(CoordinateKey.x(sectionKey) << sectionShift, CoordinateKey.z(sectionKey) << sectionShift);
    }

    private void routeScheduledTicks(WorldDataRouter router, Regionizer<RegionTickData> regionizer) {
        RoutingScheduledTicks<Block> blockTicks = (RoutingScheduledTicks<Block>) level.getBlockTicks();
        RoutingScheduledTicks<Fluid> fluidTicks = (RoutingScheduledTicks<Fluid>) level.getFluidTicks();
        blockTicks.route(chunkKey -> router.at(chunkKey).blockTicks(), () -> countScheduled(regionizer, router, RegionWorldData::blockTicks));
        fluidTicks.route(chunkKey -> router.at(chunkKey).fluidTicks(), () -> countScheduled(regionizer, router, RegionWorldData::fluidTicks));
    }

    /** Debug read from the server thread, tolerating a torn count. */
    private int countScheduled(Regionizer<RegionTickData> regionizer, WorldDataRouter router, Function<RegionWorldData, RegionScheduledTicks<?>> index) {
        int total = index.apply(router.attached()).count();
        for (Region<RegionTickData> region : regionizer.regionsView()) {
            RegionWorldData worldData = region.data().worldData();
            if (worldData != null) {
                total += index.apply(worldData).count();
            }
        }

        return total;
    }

    void prepareAttached(Runnable work) {
        pendingWork = work;
    }

    void submit(Runnable task) {
        tasks.add(task);
    }

    @Override
    public long currentTick() {
        return level.getGameTime();
    }

    @Override
    protected void tick() {
        Runnable work = pendingWork;
        if (work == null) {
            throw new IllegalStateException("Level tick unit ticked without prepared work");
        }

        pendingWork = null;
        LevelEntityLists entityLists = ((ServerLevelEntityAccess) level).leafs$entityLists();
        WorldTickContext.enter(level, ((ServerLevelWorldAccess) level).leafs$worldData(), entityLists.attached());
        try {
            entityLists.rehomeStrays();
            StageTimings stages = stages();
            stages.beginTick(System.nanoTime());
            runQueuedTasks();
            stages.mark(TickStages.serialTasks);
            work.run();

            if (level.getGameTime() % CENSUS_INTERVAL_TICKS == 0) {
                lastChunkCount = level.getChunkSource().getLoadedChunksCount();
                lastViewChunks = ((PlayerLoaderAccess) level.getChunkSource().chunkMap).leafs$playerLoader().retainedChunks();
            }

            stages.mark(TickStages.serialManagement);
            stages.endTick(System.nanoTime());
        } finally {
            WorldTickContext.exit();
        }
    }

    /** Paused solo: vanilla drains packets while paused, so the per-player queues do too, without listener tick. */
    void tickPausedNetwork() {
        RegionContext.enter(context());
        WorldTickContext.enter(level, ((ServerLevelWorldAccess) level).leafs$worldData(), ((ServerLevelEntityAccess) level).leafs$entityLists().attached());
        try {
            RegionNetworkTick.drainPaused(level);
        } finally {
            WorldTickContext.exit();
            RegionContext.exit();
        }
    }

    /** Time-boxed on the budget all dimensions share; at least one task runs, a slow one logs its class. */
    private void runQueuedTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            long start = System.nanoTime();
            task.run();
            long end = System.nanoTime();
            long millis = (end - start) / 1_000_000L;
            if (millis > 50) {
                Leafs.LOGGER.warn("Level-serial task {} ran {} ms on {}", task.getClass().getName(), millis, dimension());
            }

            if (serialBudget.expired(end)) {
                break;
            }
        }
    }

    /** Last on-owner census; readable from any thread, at most {@value #CENSUS_INTERVAL_TICKS} ticks old. */
    public int chunkCount() {
        return lastChunkCount;
    }

    /** Chunks the player view pipelines retain a ticket on; a count that never falls back after a wave names a leak. */
    public int viewChunks() {
        return lastViewChunks;
    }

    /** Sum of the tick list sizes, O(regions), any thread. */
    public int entityCount() {
        int entities = ((ServerLevelEntityAccess) level).leafs$entityLists().attached().tickList().size();
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null && !handle.isCancelled()) {
                entities += handle.entityCount();
            }
        }

        return entities;
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), chunkCount(), entityCount());
    }
}
