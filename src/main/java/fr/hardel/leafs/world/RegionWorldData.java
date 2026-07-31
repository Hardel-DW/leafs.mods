package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

import java.util.Set;
import java.util.function.LongPredicate;

/**
 * One region's world-tick state. Owns the clock, the scheduled-tick indexes and the sub-tick
 * counter; adopts the level's block events, random, neighbor updater and broadcast set while the
 * synthetic whole-level unit is the only region (M7 turns those into per-region instances).
 */
public final class RegionWorldData {
    private final RegionClock clock;
    private final RegionScheduledTicks<Block> blockTicks;
    private final RegionScheduledTicks<Fluid> fluidTicks;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    private final RandomSource random;
    private final CollectingNeighborUpdater neighborUpdater;
    private final Set<ChunkHolder> broadcastHolders;
    private long subTick;

    public RegionWorldData(RegionClock clock, LongPredicate tickCheck, ObjectLinkedOpenHashSet<BlockEventData> blockEvents, RandomSource random, CollectingNeighborUpdater neighborUpdater, Set<ChunkHolder> broadcastHolders) {
        this.clock = clock;
        this.blockTicks = new RegionScheduledTicks<>(tickCheck);
        this.fluidTicks = new RegionScheduledTicks<>(tickCheck);
        this.blockEvents = blockEvents;
        this.random = random;
        this.neighborUpdater = neighborUpdater;
        this.broadcastHolders = broadcastHolders;
    }

    public RegionClock clock() {
        return clock;
    }

    public RegionScheduledTicks<Block> blockTicks() {
        return blockTicks;
    }

    public RegionScheduledTicks<Fluid> fluidTicks() {
        return fluidTicks;
    }

    public ObjectLinkedOpenHashSet<BlockEventData> blockEvents() {
        return blockEvents;
    }

    public RandomSource random() {
        return random;
    }

    public CollectingNeighborUpdater neighborUpdater() {
        return neighborUpdater;
    }

    public Set<ChunkHolder> broadcastHolders() {
        return broadcastHolders;
    }

    public long nextSubTick() {
        return subTick++;
    }
}
