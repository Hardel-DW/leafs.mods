package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.chunk.SpawnProximity;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.TicketTimeoutIndex;
import fr.hardel.leafs.chunk.loader.PlayerChunkLoader;
import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.entity.ServerEntityAccess;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.TickGuard;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import fr.hardel.leafs.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * The region tick body: every chunk-anchored phase of the vanilla level tick, run over one region's
 * owned chunks in vanilla order. This is world/ code calling vanilla per-chunk primitives, never a
 * re-entry of {@code ServerLevel.tick}; the level-serial remainder keeps everything level-wide.
 */
public final class RegionTickBody {
    private static final int EMPTY_LEVEL_ENTITY_SKIP_TICKS = 300;
    private static final long PERSISTENT_SPAWN_PERIOD = 400L;

    private final ServerLevel level;
    private final BiConsumer<BlockPos, Block> guardedBlockTick;
    private final BiConsumer<BlockPos, Fluid> guardedFluidTick;

    public RegionTickBody(ServerLevel level) {
        this.level = level;
        this.guardedBlockTick = TickGuard.guardingWithRetry(level::tickBlock, this::requeueBlockTick);
        this.guardedFluidTick = TickGuard.guardingWithRetry(level::tickFluid, this::requeueFluidTick);
    }

    private void requeueBlockTick(BlockPos pos, Block block) {
        WorldTickContext.activeFor(level).requeueBlockTick(pos, block);
    }

    private void requeueFluidTick(BlockPos pos, Fluid fluid) {
        WorldTickContext.activeFor(level).requeueFluidTick(pos, fluid);
    }

    public ServerLevel level() {
        return level;
    }

    public void tick(Region<?> region, RegionWorldData worldData, RegionEntityData entityData, long tickCount, StageTimings stages) {
        worldData.clock().advance(tickCount);
        purgeTimedOutTickets(region);
        stages.mark(TickStages.regionTickets);
        entityData.tickList().beginTick();
        entityData.navigatingMobs().beginTick();
        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.drainOnRegion(player, level);
            }
        });
        stages.mark(TickStages.regionPackets);
        TickRateManager tickRateManager = level.tickRateManager();
        boolean runs = tickRateManager.runsNormally();
        boolean debug = level.isDebug();
        if (runs && !debug) {
            worldData.drainBlockTicks(guardedBlockTick);
            stages.mark(TickStages.regionBlockTicks);
            worldData.drainFluidTicks(guardedFluidTick);
            stages.mark(TickStages.regionFluidTicks);
            tickChunks(region, worldData, stages);
            stages.mark(TickStages.regionChunkTick);
        }

        broadcastChangedChunks(worldData);
        stages.mark(TickStages.regionBroadcast);
        RegionEntityTracking.tickRegion(level, entityData.tickList());
        stages.mark(TickStages.regionTracking);
        ServerChunkCache chunkSource = level.getChunkSource();
        LongPredicate tickingChunk = chunkSource::isPositionTicking;
        if (runs) {
            worldData.runBlockEvents(pos -> tickingChunk.test(ChunkPos.pack(pos)), this::runBlockEvent);
        }

        stages.mark(TickStages.regionBlockEvents);
        if (level.emptyTime < EMPTY_LEVEL_ENTITY_SKIP_TICKS) {
            tickEntities(tickRateManager, entityData);
            stages.mark(TickStages.regionEntities);
            worldData.blockEntityTickers().tickAll(runs, tickingChunk);
            stages.mark(TickStages.regionBlockEntities);
        }

        ((ServerEntityAccess) level.getServer()).leafs$entitySchedulers().tickOwned(level);
        PlayerChunkLoader loader = ((PlayerLoaderAccess) chunkSource.chunkMap).leafs$playerLoader();
        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                loader.tick(player);
                TickGuard.tickOrSkip(ticked -> RegionNetworkTick.tickListenerOnRegion(ticked, level.getServer()), player);
                player.connection.chunkSender.sendNextChunks(player);
                player.connection.connection.flushChannel();
            }
        });
        stages.mark(TickStages.regionPlayers);
    }

    /** The region's own timeout tickets count down here; an expiry retires its holder level, so the propagator drains right after. */
    private void purgeTimedOutTickets(Region<?> region) {
        TicketTimeoutIndex timeouts = ((TicketStorageAccess) level.getChunkSource().ticketStorage).leafs$timeouts();
        if (timeouts == null || timeouts.isEmpty()) {
            return;
        }

        if (timeouts.purgeSections(region.sectionKeySnapshot()) > 0) {
            PropagatorAccess access = (PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager();
            access.leafs$propagator().drain();
            access.leafs$simulation().drain();
        }
    }

    /** What is left of the serial {@code tickChunks} pass: the loaders of players no region ticks, then the custom spawners. */
    public void tickSerialRemainder(boolean spawnEnemies) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        PlayerChunkLoader loader = ((PlayerLoaderAccess) chunkMap).leafs$playerLoader();
        for (ServerPlayer player : chunkMap.playerMap.getAllPlayers()) {
            if (!RegionNetworkTick.ownedByRegion(player.connection)) {
                loader.tick(player);
            }
        }

        if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            level.tickCustomSpawners(spawnEnemies);
        }
    }

    private void tickChunks(Region<?> region, RegionWorldData worldData, StageTimings stages) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        long gameTime = level.getGameTime();
        long timeDiff = worldData.advanceInhabitedTime(gameTime);
        int tickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<LevelChunk> spawningChunks = new ArrayList<>();
        List<LevelChunk> randomTickingChunks = new ArrayList<>();
        int spawnableChunks = countAndCollect(region, chunkMap, spawningChunks, randomTickingChunks);
        NaturalSpawner.SpawnState state = spawningChunks.isEmpty() ? null : NaturalSpawner.createState(spawnableChunks, collectRegionEntities(region), (chunkKey, output) -> {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            if (holder != null) {
                holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(output);
            }
        }, new LocalMobCapCalculator(chunkMap));
        List<MobCategory> categories = state == null || !level.getGameRules().get(GameRules.SPAWN_MOBS)
            ? List.of()
            : NaturalSpawner.getFilteredSpawningCategories(state, chunkSource.spawnEnemies, gameTime % PERSISTENT_SPAWN_PERIOD == 0L);
        stages.mark(TickStages.regionSpawnCensus);
        Util.shuffle(spawningChunks, level.getRandom());
        for (LevelChunk chunk : spawningChunks) {
            ChunkPos chunkPos = chunk.getPos();
            chunk.incrementInhabitedTime(timeDiff);
            if (distanceManager.inEntityTickingRange(chunkPos.pack())) {
                level.tickThunder(chunk);
            }

            if (!categories.isEmpty() && level.canSpawnEntitiesInChunk(chunkPos)) {
                TickGuard.tickOrSkip(spawning -> NaturalSpawner.spawnForChunk(level, spawning, state, categories), chunk);
            }
        }

        for (LevelChunk chunk : randomTickingChunks) {
            level.tickChunk(chunk, tickSpeed);
        }
    }

    /** One pass over the owned chunks: the spawnable census, the spawning list and the random-tick list. */
    private int countAndCollect(Region<?> region, ChunkMap chunkMap, List<LevelChunk> spawningChunks, List<LevelChunk> randomTickingChunks) {
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        SpawnProximity proximity = ((PropagatorAccess) distanceManager).leafs$spawnProximity();
        int[] spawnable = new int[1];
        region.forEachChunk((chunkX, chunkZ) -> {
            long chunkKey = ChunkPos.pack(chunkX, chunkZ);
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
            if (chunk == null) {
                return;
            }

            if (proximity.covered(chunkKey)) {
                spawnable[0]++;
                if (chunkMap.anyPlayerCloseEnoughForSpawningInternal(chunk.getPos())) {
                    spawningChunks.add(chunk);
                }
            }

            if (distanceManager.inEntityTickingRange(chunkKey)) {
                randomTickingChunks.add(chunk);
            }
        });

        return spawnable[0];
    }

    /** Activation hand-off: vanilla's level-wide ticker list re-buckets to the owning regions, strays stay level-serial. */
    // A ticker already asleep answers no position (Lithium); it stays level-serial where the sleeping guard applies.
    public void migrateVanillaBlockEntityTickers(LongFunction<RegionWorldData> regionByChunk) {
        List<TickingBlockEntity> vanilla = level.blockEntityTickers;
        List<TickingBlockEntity> kept = new ArrayList<>();
        for (TickingBlockEntity ticker : vanilla) {
            BlockPos pos = ticker.getPos();
            long chunkKey = ChunkPos.pack(pos);
            RegionWorldData owner = regionByChunk.apply(chunkKey);
            if (owner != null) {
                owner.blockEntityTickers().add(ticker, chunkKey);
            } else {
                kept.add(ticker);
            }
        }

        vanilla.clear();
        vanilla.addAll(kept);
    }

    /** The census matches vanilla's {@code getAllEntities()} (accessible entities), restricted to owned chunks. */
    private List<Entity> collectRegionEntities(Region<?> region) {
        EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
        List<Entity> entities = new ArrayList<>();
        region.forEachChunk((chunkX, chunkZ) -> storage.getExistingSectionsInChunk(ChunkPos.pack(chunkX, chunkZ)).forEach(section -> {
            if (section.getStatus().isAccessible()) {
                section.getEntities().forEach(entities::add);
            }
        }));

        return entities;
    }

    private void broadcastChangedChunks(RegionWorldData worldData) {
        Set<ChunkHolder> holders = worldData.broadcastHolders();
        if (holders.isEmpty()) {
            return;
        }

        for (ChunkHolder holder : holders) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk != null) {
                holder.broadcastChanges(chunk);
            }
        }

        holders.clear();
    }

    private void runBlockEvent(BlockEventData event) {
        if (level.doBlockEvent(event)) {
            level.getServer().getPlayerList().broadcast(null, event.pos().getX(), event.pos().getY(), event.pos().getZ(), 64.0, level.dimension(), new ClientboundBlockEventPacket(event.pos(), event.block(), event.paramA(), event.paramB()));
        }
    }

    // A player in a still-loading chunk waits for the 1-radius FULL completion; vanilla would sync-load under it, a worker cannot.
    private void tickEntities(TickRateManager tickRateManager, RegionEntityData entityData) {
        ServerChunkCache chunkSource = level.getChunkSource();
        DistanceManager distanceManager = chunkSource.chunkMap.getDistanceManager();
        entityData.tickList().forEach(entity -> {
            if (entity.isRemoved() || tickRateManager.isEntityFrozen(entity)) {
                return;
            }

            entity.checkDespawn();
            if (entity instanceof ServerPlayer ? chunkSource.isPositionTicking(entity.chunkPosition().pack()) : distanceManager.inEntityTickingRange(entity.chunkPosition().pack())) {
                Entity vehicle = entity.getVehicle();
                if (vehicle != null) {
                    if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                        return;
                    }

                    entity.stopRiding();
                }

                level.guardEntityTick(guarded -> TickGuard.tickOrSkip(level::tickNonPassenger, guarded), entity);
            }
        });
    }
}
