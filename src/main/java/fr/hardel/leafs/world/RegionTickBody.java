package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.chunk.SpawnProximity;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.TicketTimeoutIndex;
import fr.hardel.leafs.chunk.loader.PlayerChunkLoader;
import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.TickGuard;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.RegionClock;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.TickRateManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/** Every chunk-anchored phase of the level tick, over one region's chunks, in vanilla order. Level-wide work stays on the server thread. */
public final class RegionTickBody {
    private static final int EMPTY_LEVEL_ENTITY_SKIP_TICKS = 300;
    private static final long PERSISTENT_SPAWN_PERIOD = 400L;
    private static final Comparator<SequencedBlockEvent> BLOCK_EVENT_ORDER = Comparator.comparingLong(SequencedBlockEvent::sequence);

    /** A block event with the level-wide sequence it was posted at, so a region replays vanilla's FIFO across its chunks. */
    private record SequencedBlockEvent(long sequence, BlockEventData event) {
    }

    private final ServerLevel level;
    private final ChunkMailbox mailbox;
    private final RegionAutosave autosave;
    private final BiConsumer<BlockPos, Block> guardedBlockTick;
    private final BiConsumer<BlockPos, Fluid> guardedFluidTick;
    private final List<SequencedBlockEvent> blockEventBatch = new ArrayList<>();

    public RegionTickBody(ServerLevel level) {
        this.level = level;
        this.mailbox = RegionChunkAccess.scheduling(level.getChunkSource().chunkMap).mailbox();
        this.autosave = new RegionAutosave(level);
        this.guardedBlockTick = TickGuard.guardingWithRetry(level::tickBlock, (pos, block) -> level.scheduleTick(pos, block, 1));
        this.guardedFluidTick = TickGuard.guardingWithRetry(level::tickFluid, (pos, fluid) -> level.scheduleTick(pos, fluid, 1));
    }

    public ServerLevel level() {
        return level;
    }

    public void tick(Region<?> region, RegionClock clock, RegionWorldData worldData, StageTimings stages, long autosaveEpoch) {
        TickRateManager tickRateManager = level.tickRateManager();
        boolean runs = tickRateManager.runsNormally();
        if (runs) {
            clock.advance();
        }

        purgeTimedOutTickets(region);
        RegionChunks chunks = drainMail(region, worldData);
        stages.mark(TickStages.regionTasks);
        ServerChunkCache chunkSource = level.getChunkSource();
        RegionEntities entities = worldData.entities();
        entities.refresh(level.entityManager.sectionStorage, chunks.holders());
        stages.mark(TickStages.regionTickets);
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                RegionNetworkTick.drainOnRegion(player, level);
            }
        });
        stages.mark(TickStages.regionPackets);
        if (runs && !level.isDebug()) {
            long currentTick = clock.currentTick();
            worldData.blockTicks().drain(chunks.ticking(), level::isPositionTickingWithEntitiesLoaded, currentTick, guardedBlockTick);
            stages.mark(TickStages.regionBlockTicks);
            worldData.fluidTicks().drain(chunks.ticking(), level::isPositionTickingWithEntitiesLoaded, currentTick, guardedFluidTick);
            stages.mark(TickStages.regionFluidTicks);
            tickChunks(chunks, worldData, stages);
            stages.mark(TickStages.regionChunkTick);
        }

        broadcastChangedChunks(chunks);
        stages.mark(TickStages.regionBroadcast);
        RegionEntityTracking.tickRegion(level, entities);
        stages.mark(TickStages.regionTracking);
        if (runs) {
            runBlockEvents(chunks);
        }

        stages.mark(TickStages.regionBlockEvents);
        if (level.emptyTime < EMPTY_LEVEL_ENTITY_SKIP_TICKS) {
            tickEntities(tickRateManager, entities);
            stages.mark(TickStages.regionEntities);
            tickBlockEntities(region, runs, chunks);
            stages.mark(TickStages.regionBlockEntities);
        }

        PlayerChunkLoader loader = ((PlayerLoaderAccess) chunkSource.chunkMap).leafs$playerLoader();
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                loader.tick(player);
                TickGuard.tickOrSkip(ticked -> RegionNetworkTick.tickListenerOnRegion(ticked, level.getServer()), player);
                player.connection.chunkSender.sendNextChunks(player);
                player.connection.connection.flushChannel();
            }
        });
        stages.mark(TickStages.regionPlayers);
        autosave.tick(region, chunks, entities, autosaveEpoch);
        stages.mark(TickStages.regionAutosave);
    }

    /** The photo of the region's chunks, then the mail of every chunk in it; the entity photo comes after, so an arrival ticks this pass. */
    public RegionChunks drainMail(Region<?> region, RegionWorldData worldData) {
        RegionChunks chunks = worldData.chunks();
        chunks.refresh(region, level.getChunkSource().chunkMap);
        mailbox.drain(chunks.holders());
        return chunks;
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
            if (!RegionNetworkTick.ownedByRegion(player)) {
                loader.tick(player);
            }
        }

        if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            level.tickCustomSpawners(spawnEnemies);
        }
    }

    private void tickChunks(RegionChunks chunks, RegionWorldData worldData, StageTimings stages) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        long gameTime = level.getGameTime();
        long timeDiff = worldData.advanceInhabitedTime(gameTime);
        int tickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<LevelChunk> spawningChunks = new ArrayList<>();
        List<LevelChunk> randomTickingChunks = new ArrayList<>();
        int spawnableChunks = countAndCollect(chunks, chunkMap, spawningChunks, randomTickingChunks);
        NaturalSpawner.SpawnState state = spawningChunks.isEmpty() ? null : NaturalSpawner.createState(spawnableChunks, collectAccessibleEntities(chunks), (chunkKey, output) -> {
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

    /** One pass over the ticking chunks: the spawnable census, the spawning list and the random-tick list. */
    private int countAndCollect(RegionChunks chunks, ChunkMap chunkMap, List<LevelChunk> spawningChunks, List<LevelChunk> randomTickingChunks) {
        DistanceManager distanceManager = chunkMap.getDistanceManager();
        SpawnProximity proximity = ((PropagatorAccess) distanceManager).leafs$spawnProximity();
        int spawnable = 0;
        for (LevelChunk chunk : chunks.ticking()) {
            long chunkKey = chunk.getPos().pack();
            if (proximity.covered(chunkKey)) {
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

    /** The census matches vanilla's {@code getAllEntities()} (accessible entities), restricted to owned chunks. */
    private List<Entity> collectAccessibleEntities(RegionChunks chunks) {
        EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
        List<Entity> entities = new ArrayList<>();
        for (ChunkHolder holder : chunks.holders()) {
            storage.getExistingSectionsInChunk(holder.getPos().pack()).forEach(section -> {
                if (section.getStatus().isAccessible()) {
                    section.getEntities().forEach(entities::add);
                }
            });
        }

        return entities;
    }

    private static void broadcastChangedChunks(RegionChunks chunks) {
        for (ChunkHolder holder : chunks.holders()) {
            LevelChunk chunk = holder.hasChangesToBroadcast() ? holder.getTickingChunk() : null;
            if (chunk != null) {
                holder.broadcastChanges(chunk);
            }
        }
    }

    /** Vanilla's runBlockEvents: every ticking chunk's events in posting order, cascades replayed until nothing is left. */
    private void runBlockEvents(RegionChunks chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        do {
            blockEventBatch.clear();
            for (LevelChunk chunk : chunks.ticking()) {
                ChunkBlockEvents events = ((ChunkTickAccess) chunk).leafs$blockEvents();
                if (!events.isEmpty() && chunkSource.isPositionTicking(chunk.getPos().pack())) {
                    events.drainTo((event, sequence) -> blockEventBatch.add(new SequencedBlockEvent(sequence, event)));
                }
            }

            blockEventBatch.sort(BLOCK_EVENT_ORDER);
            for (SequencedBlockEvent sequenced : blockEventBatch) {
                runBlockEvent(sequenced.event());
            }
        } while (!blockEventBatch.isEmpty());
    }

    private void runBlockEvent(BlockEventData event) {
        if (level.doBlockEvent(event)) {
            level.getServer().getPlayerList().broadcast(null, event.pos().getX(), event.pos().getY(), event.pos().getZ(), 64.0, level.dimension(), new ClientboundBlockEventPacket(event.pos(), event.block(), event.paramA(), event.paramB()));
        }
    }

    // A player in a still-loading chunk waits for the 1-radius FULL completion; vanilla would sync-load under it, a worker cannot.
    private void tickEntities(TickRateManager tickRateManager, RegionEntities entities) {
        ServerChunkCache chunkSource = level.getChunkSource();
        DistanceManager distanceManager = chunkSource.chunkMap.getDistanceManager();
        entities.forEach(entity -> {
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
