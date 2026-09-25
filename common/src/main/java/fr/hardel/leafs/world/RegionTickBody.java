package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.view.PlayerView;
import fr.hardel.leafs.entity.EntityTickAccess;
import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionClock;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public final class RegionTickBody {
    private static final int EMPTY_LEVEL_ENTITY_SKIP_TICKS = 300;
    private static final long PERSISTENT_SPAWN_PERIOD = 400L;

    private final ServerLevel level;
    private final ChunkSaves saves;
    private final MobCaps mobCaps;

    public RegionTickBody(ServerLevel level) {
        this.level = level;
        this.saves = new ChunkSaves(level);
        this.mobCaps = new MobCaps(level);
    }

    public ServerLevel level() {
        return level;
    }

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

        ChunkBroadcasts.changed(((ChangedChunksAccess) level.getChunkSource()).leafs$changedHolders(), owned.holders());
        stages.mark(TickStages.regionBroadcast);
        RegionEntityTracking.tickRegion(level, owned, entities);
        stages.mark(TickStages.regionTracking);
        if (runs) {
            runBlockEvents(owned);
        }

        stages.mark(TickStages.regionBlockEvents);
        if (level.emptyTime < EMPTY_LEVEL_ENTITY_SKIP_TICKS) {
            tickEntities(tickRateManager, entities);
            stages.mark(TickStages.regionEntities);
            tickBlockEntities(runs, owned);
            stages.mark(TickStages.regionBlockEntities);
        }

        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.tickPlayerOnRegion(player, level.getServer());
            }
        });

        stages.mark(TickStages.regionPlayers);
        long slice = level.tickRateManager().nanosecondsPerTick() / 10;
        saves.autosave(worldData, regions.autosaveEpoch(), System.nanoTime() + slice);
        stages.mark(TickStages.regionAutosave);
        RegionInbox inbox = region.data().inbox();
        inbox.drain(Math.max(tickDeadlineNanos, System.nanoTime() + slice));
        stages.mark(TickStages.regionTasks);
    }

    public void tickSerial(boolean spawnEnemies) {
        mobCaps.sumLevel();
        LevelChunks.of(level).sweep().soon();
        if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            level.tickCustomSpawners(spawnEnemies);
        }
    }

    private void tickChunks(RegionChunks chunks, RegionWorldData worldData, PlayerView view, StageTimings stages) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;
        long gameTime = level.getGameTime();
        long timeDiff = worldData.advanceInhabitedTime(gameTime);
        int tickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<LevelChunk> spawningChunks = new ArrayList<>();
        List<LevelChunk> randomTickingChunks = new ArrayList<>();
        int spawnableChunks = countAndCollect(chunks, chunkMap, view, spawningChunks, randomTickingChunks);
        NaturalSpawner.SpawnState state = NaturalSpawner.createState(spawnableChunks, worldData.entities().accessible(), chunkSource::getFullChunk,
            new LocalMobCapCalculator(chunkMap));
        worldData.publishCensus(MobCensus.of(state));
        List<MobCategory> categories = level.getGameRules().get(GameRules.SPAWN_MOBS)
            ? mobCaps.spawnable(worldData, chunkSource.spawnEnemies, gameTime % PERSISTENT_SPAWN_PERIOD == 0L)
            : List.of();

        stages.mark(TickStages.regionSpawnCensus);
        Util.shuffle(spawningChunks, level.getRandom());
        for (LevelChunk chunk : spawningChunks) {
            chunkSource.tickSpawningChunk(chunk, timeDiff, categories, state);
        }

        for (LevelChunk chunk : randomTickingChunks) {
            level.tickChunk(chunk, tickSpeed);
        }
    }

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

    private void runBlockEvents(RegionChunks chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        List<ChunkBlockEvents> sets = new ArrayList<>();
        for (LevelChunk chunk : chunks.ticking()) {
            if (chunkSource.isPositionTicking(chunk.getPos().pack())) {
                sets.add(((ChunkTickAccess) chunk).leafs$blockEvents());
            }
        }

        ChunkBlockEvents.runAll(sets, this::runBlockEvent);
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

            EntityTickAccess claim = (EntityTickAccess) entity;
            if (!claim.leafs$beginTick()) {
                return;
            }

            try {
                tickEntity(entity, distanceManager);
            } finally {
                claim.leafs$endTick();
            }
        });
    }

    private void tickEntity(Entity entity, DistanceManager distanceManager) {
        entity.checkDespawn();
        long chunk = entity.chunkPosition().pack();
        if (!(entity instanceof ServerPlayer) && !distanceManager.inEntityTickingRange(chunk)) {
            return;
        }

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                return;
            }

            entity.stopRiding();
        }

        level.guardEntityTick(level::tickNonPassenger, entity);
    }

    private void tickBlockEntities(boolean runsNormally, RegionChunks chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        for (LevelChunk chunk : chunks.ticking()) {
            if (chunkSource.isPositionTicking(chunk.getPos().pack())) {
                ((ChunkTickAccess) chunk).leafs$tickers().tickAll(runsNormally);
            }
        }
    }
}
