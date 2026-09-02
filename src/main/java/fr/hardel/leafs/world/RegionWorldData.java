package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import java.util.function.LongSupplier;

/** What a region carries between ticks: its clock's view, its random, its updaters, and the two photos it retakes every tick. Nothing here moves on a merge or a split. */
public final class RegionWorldData {
    private final LongSupplier time;
    private final RandomSource random;
    private final CollectingNeighborUpdater neighborUpdater;
    private final PathTypeCache pathTypeCache;
    private final ScheduledTickDrain<LevelChunk, Block> blockTicks = new ScheduledTickDrain<>(chunk -> chunk.blockTicks, chunk -> chunk.getPos().pack());
    private final ScheduledTickDrain<LevelChunk, Fluid> fluidTicks = new ScheduledTickDrain<>(chunk -> chunk.fluidTicks, chunk -> chunk.getPos().pack());
    private final BlockEventBatch<LevelChunk> blockEvents = new BlockEventBatch<>(chunk -> ((ChunkTickAccess) chunk).leafs$blockEvents());
    private final RegionChunks chunks = new RegionChunks();
    private final RegionEntities entities = new RegionEntities();
    private volatile MobCensus census = MobCensus.EMPTY;
    private long subTick;
    private long lastInhabitedUpdate;

    public RegionWorldData(LongSupplier time, RandomSource random, CollectingNeighborUpdater neighborUpdater, PathTypeCache pathTypeCache, long inhabitedFrom) {
        this.time = time;
        this.random = random;
        this.neighborUpdater = neighborUpdater;
        this.pathTypeCache = pathTypeCache;
        this.lastInhabitedUpdate = inhabitedFrom;
    }

    public static RegionWorldData regional(ServerLevel level, LongSupplier time) {
        return new RegionWorldData(time, RandomSource.create(), new CollectingNeighborUpdater(level, level.getServer().getMaxChainedNeighborUpdates()), new PathTypeCache(), level.getGameTime());
    }

    public long currentTick() {
        return time.getAsLong();
    }

    public RandomSource random() {
        return random;
    }

    public CollectingNeighborUpdater neighborUpdater() {
        return neighborUpdater;
    }

    public PathTypeCache pathTypeCache() {
        return pathTypeCache;
    }

    public ScheduledTickDrain<LevelChunk, Block> blockTicks() {
        return blockTicks;
    }

    public ScheduledTickDrain<LevelChunk, Fluid> fluidTicks() {
        return fluidTicks;
    }

    public BlockEventBatch<LevelChunk> blockEvents() {
        return blockEvents;
    }

    public RegionChunks chunks() {
        return chunks;
    }

    public RegionEntities entities() {
        return entities;
    }

    public MobCensus census() {
        return census;
    }

    public void publishCensus(MobCensus census) {
        this.census = census;
    }

    /** Vanilla's shape on the region clock; the sub-tick counter keeps the drain order deterministic within a tick. */
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return new ScheduledTick<>(type, pos, currentTick() + delay, priority, subTick++);
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return new ScheduledTick<>(type, pos, currentTick() + delay, subTick++);
    }

    public long advanceInhabitedTime(long gameTime) {
        long delta = gameTime - lastInhabitedUpdate;
        lastInhabitedUpdate = gameTime;
        return delta;
    }
}
