package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.ticking.RegionTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

public final class RegionWorldData {
    private final RegionTime time;
    private final RandomSource random;
    private final CollectingNeighborUpdater neighborUpdater;
    private final PathTypeCache pathTypeCache;
    private final ScheduledTickDrain<LevelChunk, Block> blockTicks = new ScheduledTickDrain<>(chunk -> chunk.blockTicks, chunk -> chunk.getPos().pack());
    private final ScheduledTickDrain<LevelChunk, Fluid> fluidTicks = new ScheduledTickDrain<>(chunk -> chunk.fluidTicks, chunk -> chunk.getPos().pack());
    private final RegionChunks chunks = new RegionChunks();
    private final RegionEntities entities = new RegionEntities();
    private volatile MobCensus census = MobCensus.EMPTY;
    private long subTick;
    private long savedEpoch;

    public RegionWorldData(RegionTime time, RandomSource random, CollectingNeighborUpdater neighborUpdater, PathTypeCache pathTypeCache) {
        this.time = time;
        this.random = random;
        this.neighborUpdater = neighborUpdater;
        this.pathTypeCache = pathTypeCache;
    }

    public static RegionWorldData regional(ServerLevel level, RegionTime time) {
        return new RegionWorldData(time, RandomSource.create(), new CollectingNeighborUpdater(level, level.getServer().getMaxChainedNeighborUpdates()), new PathTypeCache());
    }

    public RegionTime time() {
        return time;
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

    public long nextSubTickCount() {
        return subTick++;
    }

    public long savedEpoch() {
        return savedEpoch;
    }

    public void markEpochSaved(long epoch) {
        savedEpoch = epoch;
    }

    public void forgetEpoch() {
        savedEpoch = Long.MIN_VALUE;
    }
}
