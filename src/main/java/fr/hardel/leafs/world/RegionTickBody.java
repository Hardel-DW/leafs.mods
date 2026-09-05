package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.ChunkBroadcasts;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.view.PlayerView;
import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionClock;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** Every chunk-anchored phase of the level tick, over one region's chunks, in vanilla order. Level-wide work stays on the server thread. */
public final class RegionTickBody {
    private static final int EMPTY_LEVEL_ENTITY_SKIP_TICKS = 300;
    private static final long PERSISTENT_SPAWN_PERIOD = 400L;

    private final ServerLevel level;
    private final RegionAutosave autosave;
    private final MobCaps mobCaps;

    public RegionTickBody(ServerLevel level) {
        this.level = level;
        this.autosave = new RegionAutosave(level);
        this.mobCaps = new MobCaps(level);
    }

    public ServerLevel level() {
        return level;
    }

    /** The save takes a tenth of the period at most, the inbox what is left of it and a tenth at least: a heavy tick still publishes, a light one publishes everything. */
    public void tick(Region<RegionTickData> region, RegionClock clock, RegionWorldData worldData, StageTimings stages, LevelRegions regions, long tickDeadlineNanos) {
        TickRateManager tickRateManager = level.tickRateManager();
        boolean runs = tickRateManager.runsNormally();
        if (runs) {
            clock.advance();
        }

        LevelChunks chunks = LevelChunks.of(level);
        chunks.timeouts().purgeSections(region.sectionKeySnapshot());
        RegionChunks owned = worldData.chunks();
        owned.refresh(region, level.getChunkSource().chunkMap);
        stages.mark(TickStages.regionTickets);
        RegionEntities entities = worldData.entities();
        entities.refresh(level, owned.holders());
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.drainOnRegion(player, level);
            }
        });
        stages.mark(TickStages.regionPackets);
        if (runs && !level.isDebug()) {
            long currentTick = clock.currentTick();
            worldData.blockTicks().drain(owned.ticking(), level::isPositionTickingWithEntitiesLoaded, currentTick, level::tickBlock);
            stages.mark(TickStages.regionBlockTicks);
            worldData.fluidTicks().drain(owned.ticking(), level::isPositionTickingWithEntitiesLoaded, currentTick, level::tickFluid);
            stages.mark(TickStages.regionFluidTicks);
            tickChunks(owned, worldData, chunks.view(), stages);
            stages.mark(TickStages.regionChunkTick);
        }

        ChunkBroadcasts.changed(owned.holders());
        stages.mark(TickStages.regionBroadcast);
        RegionEntityTracking.tickRegion(level, owned, entities);
        stages.mark(TickStages.regionTracking);
        if (runs) {
            runBlockEvents(owned, worldData);
        }

        stages.mark(TickStages.regionBlockEvents);
        if (level.emptyTime < EMPTY_LEVEL_ENTITY_SKIP_TICKS) {
            tickEntities(tickRateManager, entities);
            stages.mark(TickStages.regionEntities);
            tickBlockEntities(region, runs, owned);
            stages.mark(TickStages.regionBlockEntities);
        }

        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.tickPlayerOnRegion(player, level.getServer());
            }
        });
        stages.mark(TickStages.regionPlayers);
        long slice = regions.tickPeriodNanos() / 10;
        autosave.tick(region, worldData, regions.autosaveEpoch(), regions.autosaveForced(), System.nanoTime() + slice);
        stages.mark(TickStages.regionAutosave);
        RegionInbox inbox = region.data().inbox();
        inbox.drain(Math.max(tickDeadlineNanos, System.nanoTime() + slice));
        stages.mark(TickStages.regionTasks);
    }

    /** What is left of the serial {@code tickChunks} pass: the sweep of the chunks no region covers, then the custom spawners. */
    public void tickSerial(boolean spawnEnemies) {
        LevelChunks.of(level).sweep().soon();
        if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            level.tickCustomSpawners(spawnEnemies);
        }
    }

    private void tickChunks(RegionChunks chunks, RegionWorldData worldData, PlayerView view, StageTimings stages) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        long gameTime = level.getGameTime();
        long timeDiff = worldData.advanceInhabitedTime(gameTime);
        int tickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<LevelChunk> spawningChunks = new ArrayList<>();
        List<LevelChunk> randomTickingChunks = new ArrayList<>();
        int spawnableChunks = countAndCollect(chunks, chunkMap, view, spawningChunks, randomTickingChunks);
        NaturalSpawner.SpawnState state = spawningChunks.isEmpty() ? null : NaturalSpawner.createState(spawnableChunks, worldData.entities().accessible(), (chunkKey, output) -> {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            if (holder != null) {
                holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(output);
            }
        }, new LocalMobCapCalculator(chunkMap));
        List<MobCategory> categories = state == null || !level.getGameRules().get(GameRules.SPAWN_MOBS)
            ? List.of()
            : mobCaps.spawnable(worldData, state, chunkSource.spawnEnemies, gameTime % PERSISTENT_SPAWN_PERIOD == 0L);
        stages.mark(TickStages.regionSpawnCensus);
        Util.shuffle(spawningChunks, level.getRandom());
        for (LevelChunk chunk : spawningChunks) {
            ChunkPos chunkPos = chunk.getPos();
            chunk.incrementInhabitedTime(timeDiff);
            if (distanceManager.inEntityTickingRange(chunkPos.pack())) {
                level.tickThunder(chunk);
            }

            if (!categories.isEmpty() && level.canSpawnEntitiesInChunk(chunkPos)) {
                NaturalSpawner.spawnForChunk(level, chunk, state, categories);
            }
        }

        for (LevelChunk chunk : randomTickingChunks) {
            level.tickChunk(chunk, tickSpeed);
        }
    }

    /** One pass over the ticking chunks: the spawnable census, the spawning list and the random-tick list. */
    private int countAndCollect(RegionChunks chunks, ChunkMap chunkMap, PlayerView view, List<LevelChunk> spawningChunks, List<LevelChunk> randomTickingChunks) {
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        int spawnable = 0;
        for (LevelChunk chunk : chunks.ticking()) {
            long chunkKey = chunk.getPos().pack();
            if (view.nearby(chunkKey) != TriState.FALSE) {
                spawnable++;
                if (chunkMap.anyPlayerCloseEnoughForSpawningInternal(chunk.getPos())) {
                    spawningChunks.add(chunk);
                }
            }

            if (distanceManager.inEntityTickingRange(chunkKey)) {
                randomTickingChunks.add(chunk);
            }
        }

        return spawnable;
    }

    private void runBlockEvents(RegionChunks chunks, RegionWorldData worldData) {
        ServerChunkCache chunkSource = level.getChunkSource();
        worldData.blockEvents().run(chunks.ticking(), chunk -> chunkSource.isPositionTicking(chunk.getPos().pack()), this::runBlockEvent);
    }

    private void runBlockEvent(BlockEventData event) {
        if (level.doBlockEvent(event)) {
            ClientboundBlockEventPacket packet = new ClientboundBlockEventPacket(event.pos(), event.block(), event.paramA(), event.paramB());
            level.getServer().getPlayerList().broadcast(null, event.pos().getX(), event.pos().getY(), event.pos().getZ(), 64.0, level.dimension(), packet);
        }
    }

    private void tickEntities(TickRateManager tickRateManager, RegionEntities entities) {
        ServerChunkCache chunkSource = level.getChunkSource();
        DistanceManager distanceManager = chunkSource.chunkMap.getDistanceManager();
        entities.forEach(entity -> {
            if (entity.isRemoved() || tickRateManager.isEntityFrozen(entity)) {
                return;
            }

            entity.checkDespawn();
            long chunk = entity.chunkPosition().pack();
            if (entity instanceof ServerPlayer || distanceManager.inEntityTickingRange(chunk)) {
                Entity vehicle = entity.getVehicle();
                if (vehicle != null) {
                    if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                        return;
                    }

                    entity.stopRiding();
                }

                level.guardEntityTick(level::tickNonPassenger, entity);
            }
        });
    }

    private void tickBlockEntities(Region<?> region, boolean runsNormally, RegionChunks chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        for (LevelChunk chunk : chunks.ticking()) {
            if (chunkSource.isPositionTicking(chunk.getPos().pack())) {
                ((ChunkTickAccess) chunk).leafs$tickers().tickAll(runsNormally);
            }
        }

        if (runsNormally) {
            ((ServerLevelRegionAccess) level).leafs$anchors().tick(region, chunkSource::isPositionTicking);
        }
    }
}
