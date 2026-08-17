package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/** Merge and split only run between region ticks, under the regionizer's write lock. */
public final class RegionWorldData {
    private static final int MAX_SCHEDULED_TICKS_PER_DRAIN = 65536;

    private final RegionClock clock;
    private final RegionScheduledTicks<Block> blockTicks;
    private final RegionScheduledTicks<Fluid> fluidTicks;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    private final RandomSource random;
    private final CollectingNeighborUpdater neighborUpdater;
    private final Set<ChunkHolder> broadcastHolders;
    private final RegionBlockEntityTickers blockEntityTickers = new RegionBlockEntityTickers();
    private final PathTypeCache pathTypeCache;
    private long subTick;
    private long lastInhabitedUpdate;

    public RegionWorldData(RegionClock clock, LongPredicate tickCheck, ObjectLinkedOpenHashSet<BlockEventData> blockEvents, RandomSource random, CollectingNeighborUpdater neighborUpdater, Set<ChunkHolder> broadcastHolders, PathTypeCache pathTypeCache) {
        this.clock = clock;
        this.blockTicks = new RegionScheduledTicks<>(tickCheck);
        this.fluidTicks = new RegionScheduledTicks<>(tickCheck);
        this.blockEvents = blockEvents;
        this.random = random;
        this.neighborUpdater = neighborUpdater;
        this.broadcastHolders = broadcastHolders;
        this.pathTypeCache = pathTypeCache;
    }

    /**
     * The counter starts at the level's CURRENT game time: deadlines scheduled against game time
     * before activation (spawn-chunk unpack) then read correctly without a migration rebase, and a
     * chunk moving between units stays delay-consistent through pack/unpack.
     */
    public static RegionWorldData regional(ServerLevel level) {
        RegionWorldData data = new RegionWorldData(new RegionClock(level.getGameTime()), level::isPositionTickingWithEntitiesLoaded, new ObjectLinkedOpenHashSet<>(), RandomSource.create(), new CollectingNeighborUpdater(level, level.getServer().getMaxChainedNeighborUpdates()), new HashSet<>(), new PathTypeCache());
        data.lastInhabitedUpdate = level.getGameTime();

        return data;
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

    public RegionBlockEntityTickers blockEntityTickers() {
        return blockEntityTickers;
    }

    public PathTypeCache pathTypeCache() {
        return pathTypeCache;
    }

    public long nextSubTick() {
        return subTick++;
    }

    /** Deadlines are relative to this unit's clock (two clocks); the sub-tick tie-break comes from the same unit. */
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return new ScheduledTick<>(type, pos, clock.currentTick() + delay, priority, nextSubTick());
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return new ScheduledTick<>(type, pos, clock.currentTick() + delay, nextSubTick());
    }

    /** Inhabited-time bookkeeping reads global time (absolute consumer); returns the delta since the last body tick. */
    public long advanceInhabitedTime(long gameTime) {
        long delta = gameTime - lastInhabitedUpdate;
        lastInhabitedUpdate = gameTime;

        return delta;
    }

    /** Vanilla drain shape at this unit's clock time; the cap is vanilla's level-wide one, applied per region. */
    public void drainScheduledTicks(BiConsumer<BlockPos, Block> blockExecutor, BiConsumer<BlockPos, Fluid> fluidExecutor) {
        long time = clock.currentTick();
        blockTicks.tick(time, MAX_SCHEDULED_TICKS_PER_DRAIN, blockExecutor);
        fluidTicks.tick(time, MAX_SCHEDULED_TICKS_PER_DRAIN, fluidExecutor);
    }

    /** Vanilla {@code ServerLevel.runBlockEvents}: untickable positions re-queue for the next tick, cascades run this one. */
    public void runBlockEvents(Predicate<BlockPos> tickable, Consumer<BlockEventData> executor) {
        List<BlockEventData> reschedule = null;
        while (!blockEvents.isEmpty()) {
            BlockEventData event = blockEvents.removeFirst();
            if (tickable.test(event.pos())) {
                executor.accept(event);
            } else {
                if (reschedule == null) {
                    reschedule = new ArrayList<>();
                }
                reschedule.add(event);
            }
        }

        if (reschedule != null) {
            blockEvents.addAll(reschedule);
        }
    }

    public void mergeInto(RegionWorldData target) {
        long tickOffset = target.clock.currentTick() - clock.currentTick();
        blockTicks.mergeInto(target.blockTicks, tickOffset);
        fluidTicks.mergeInto(target.fluidTicks, tickOffset);
        target.blockEvents.addAll(blockEvents);
        blockEvents.clear();
        blockEntityTickers.mergeInto(target.blockEntityTickers);
        target.subTick = Math.max(target.subTick, subTick);
        target.lastInhabitedUpdate = Math.max(target.lastInhabitedUpdate, lastInhabitedUpdate);
    }

    /**
     * Activation hand-off of what accumulated before the level's first tick. Scheduled containers
     * always have an owning region (their chunk has a holder); events and tickers without one stay
     * attached, where the level-serial remainder keeps draining them.
     */
    public void migrateInto(int sectionShift, LongFunction<RegionWorldData> childBySection) {
        redistribute(sectionShift, childBySection, true);
    }

    /** State whose section died with the split is dropped, like its chunk's other transient state. */
    public void splitInto(int sectionShift, LongFunction<RegionWorldData> childBySection) {
        redistribute(sectionShift, childBySection, false);
    }

    /** Scheduled containers never go unmatched: their chunk has a holder, so an absent child is a bug, not an orphan. */
    private void redistribute(int sectionShift, LongFunction<RegionWorldData> childBySection, boolean keepOrphans) {
        blockTicks.splitInto(sectionShift, section -> {
            RegionWorldData child = childBySection.apply(section);

            return child == null ? null : child.blockTicks;
        });
        fluidTicks.splitInto(sectionShift, section -> {
            RegionWorldData child = childBySection.apply(section);

            return child == null ? null : child.fluidTicks;
        });
        List<BlockEventData> orphans = new ArrayList<>();
        for (BlockEventData event : blockEvents) {
            RegionWorldData child = childBySection.apply(CoordinateKey.pack(event.pos().getX() >> (4 + sectionShift), event.pos().getZ() >> (4 + sectionShift)));
            if (child != null) {
                child.blockEvents.add(event);
            } else if (keepOrphans) {
                orphans.add(event);
            }
        }

        blockEvents.clear();
        blockEvents.addAll(orphans);
        blockEntityTickers.redistribute(sectionShift, section -> {
            RegionWorldData child = childBySection.apply(section);
            return child == null ? null : child.blockEntityTickers;
        }, keepOrphans);
    }

    public void inheritTimeFrom(RegionWorldData parent) {
        clock.resetTo(parent.clock.currentTick());
        subTick = parent.subTick;
        lastInhabitedUpdate = parent.lastInhabitedUpdate;
    }
}
