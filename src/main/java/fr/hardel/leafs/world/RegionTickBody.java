package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.TickGuard;
import fr.hardel.leafs.region.Region;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
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

    public RegionTickBody(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    public void tick(Region<?> region, RegionWorldData worldData, RegionEntityData entityData, long tickCount) {
        worldData.clock().advance(tickCount);
        entityData.tickList().beginTick();
        entityData.navigatingMobs().beginTick();
        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.drainOnRegion(player, level);
            }
        });
        TickRateManager tickRateManager = level.tickRateManager();
        boolean runs = tickRateManager.runsNormally();
        boolean debug = level.isDebug();
        if (runs && !debug) {
            worldData.drainScheduledTicks(level::tickBlock, level::tickFluid);
        }

        if (runs && !debug) {
            tickChunks(region, worldData);
        }

        broadcastChangedChunks(worldData);
        RegionEntityTracking.tickRegion(level, entityData.tickList());
        ServerChunkCache chunkSource = level.getChunkSource();
        // Ticket AND completed 1-radius FULL: vanilla bridges the streaming gap with a sync load a region worker cannot do (Compromise #18).
        LongPredicate tickingChunk = chunkSource::isPositionTicking;
        if (runs) {
            worldData.runBlockEvents(pos -> tickingChunk.test(ChunkPos.pack(pos)), this::runBlockEvent);
        }

        if (level.emptyTime < EMPTY_LEVEL_ENTITY_SKIP_TICKS) {
            tickEntities(tickRateManager, entityData);
            worldData.blockEntityTickers().tickAll(runs, tickingChunk);
        }

        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.tickListenerOnRegion(player, level.getServer());
                player.connection.chunkSender.sendNextChunks(player);
            }
        });
    }

    /** What is left of the serial {@code tickChunks} pass: the custom spawners, decided level-serial. */
    public void tickSerialRemainder(boolean spawnEnemies) {
        if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            level.tickCustomSpawners(spawnEnemies);
        }
    }

    private void tickChunks(Region<?> region, RegionWorldData worldData) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        Long2ByteMap spawnLevels = distanceManager.naturalSpawnChunkCounter.chunks;
        long gameTime = level.getGameTime();
        long timeDiff = worldData.advanceInhabitedTime(gameTime);
        int tickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<LevelChunk> spawningChunks = new ArrayList<>();
        List<LevelChunk> randomTickingChunks = new ArrayList<>();
        int spawnableChunks = countAndCollect(region, chunkMap, spawnLevels, spawningChunks, randomTickingChunks);
        List<MobCategory> spawningCategories = List.of();
        NaturalSpawner.SpawnState spawnState = null;
        if (!spawningChunks.isEmpty()) {
            spawnState = NaturalSpawner.createState(spawnableChunks, collectRegionEntities(region), (chunkKey, output) -> {
                ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
                if (holder != null) {
                    holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(output);
                }
            }, new LocalMobCapCalculator(chunkMap));
            if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
                spawningCategories = NaturalSpawner.getFilteredSpawningCategories(spawnState, chunkSource.spawnEnemies, gameTime % PERSISTENT_SPAWN_PERIOD == 0L);
            }
        }

        Util.shuffle(spawningChunks, level.getRandom());
        NaturalSpawner.SpawnState state = spawnState;
        List<MobCategory> categories = spawningCategories;
        for (LevelChunk chunk : spawningChunks) {
            ChunkPos chunkPos = chunk.getPos();
            chunk.incrementInhabitedTime(timeDiff);
            if (distanceManager.inEntityTickingRange(chunkPos.pack())) {
                level.tickThunder(chunk);
            }

            if (!categories.isEmpty() && level.canSpawnEntitiesInChunk(chunkPos)) {
                // A spawn inside a structure's bounds may need a start chunk never loaded (Compromise #19): skip the chunk this tick.
                TickGuard.tickOrSkip(spawning -> NaturalSpawner.spawnForChunk(level, spawning, state, categories), chunk);
            }
        }

        for (LevelChunk chunk : randomTickingChunks) {
            level.tickChunk(chunk, tickSpeed);
        }
    }

    /** One pass over the owned chunks: the spawnable census, the spawning list and the random-tick list. */
    private int countAndCollect(Region<?> region, ChunkMap chunkMap, Long2ByteMap spawnLevels, List<LevelChunk> spawningChunks, List<LevelChunk> randomTickingChunks) {
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        int[] spawnable = new int[1];
        region.forEachChunk((chunkX, chunkZ) -> {
            long chunkKey = ChunkPos.pack(chunkX, chunkZ);
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            LevelChunk chunk = holder == null ? null : holder.getTickingChunk();
            if (chunk == null) {
                return;
            }

            boolean nearPlayers = spawnLevels.containsKey(chunkKey);
            if (nearPlayers) {
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
    public void migrateVanillaBlockEntityTickers(LongFunction<RegionWorldData> regionByChunk) {
        List<TickingBlockEntity> vanilla = level.blockEntityTickers;
        List<TickingBlockEntity> kept = new ArrayList<>();
        for (TickingBlockEntity ticker : vanilla) {
            BlockPos pos = ticker.getPos();
            // A ticker already asleep answers no position (Lithium); it stays level-serial where the sleeping guard applies.
            if (pos == null) {
                kept.add(ticker);
                continue;
            }

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

    private void tickEntities(TickRateManager tickRateManager, RegionEntityData entityData) {
        ServerChunkCache chunkSource = level.getChunkSource();
        DistanceManager distanceManager = chunkSource.chunkMap.getDistanceManager();
        entityData.tickList().forEach(entity -> {
            if (entity.isRemoved() || tickRateManager.isEntityFrozen(entity)) {
                return;
            }

            entity.checkDespawn();
            // A player in a still-loading chunk waits for the 1-radius FULL completion (Compromise #18): vanilla would sync-load under it, a worker cannot.
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
