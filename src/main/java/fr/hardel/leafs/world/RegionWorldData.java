package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** The world state of one tick unit, on its own time: the region clock, or game time for the attached payload. */
public final class RegionWorldData {
    private static final int MAX_SCHEDULED_TICKS_PER_DRAIN = 65536;
    private final LongSupplier gameTime;
    private final LongSupplier time;
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

    public RegionWorldData(LongSupplier gameTime, LongSupplier time, LongPredicate tickCheck, ObjectLinkedOpenHashSet<BlockEventData> blockEvents, RandomSource random, CollectingNeighborUpdater neighborUpdater, Set<ChunkHolder> broadcastHolders, PathTypeCache pathTypeCache) {
        this.gameTime = gameTime;
        this.time = time;
        this.blockTicks = new RegionScheduledTicks<>(tickCheck, gameTime, time);
        this.fluidTicks = new RegionScheduledTicks<>(tickCheck, gameTime, time);
        this.blockEvents = blockEvents;
        this.random = random;
        this.neighborUpdater = neighborUpdater;
        this.broadcastHolders = broadcastHolders;
        this.pathTypeCache = pathTypeCache;
    }

    public static RegionWorldData regional(ServerLevel level, LongSupplier time) {
        RegionWorldData data = new RegionWorldData(level::getGameTime, time, level::isPositionTickingWithEntitiesLoaded, new ObjectLinkedOpenHashSet<>(), RandomSource.create(), new CollectingNeighborUpdater(level, level.getServer().getMaxChainedNeighborUpdates()), new HashSet<>(), new PathTypeCache());
        data.lastInhabitedUpdate = level.getGameTime();

        return data;
    }

    public long currentTick() {
        return time.getAsLong();
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

    /** Vanilla's shape; the sub-tick counter is per unit so the drain order stays deterministic. */
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return new ScheduledTick<>(type, pos, gameTime.getAsLong() + delay, priority, nextSubTick());
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return new ScheduledTick<>(type, pos, gameTime.getAsLong() + delay, nextSubTick());
    }

    /** Inhabited time follows game time. */
    public long advanceInhabitedTime(long gameTime) {
        long delta = gameTime - lastInhabitedUpdate;
        lastInhabitedUpdate = gameTime;
        return delta;
    }

    /** Vanilla's level-wide cap, applied per region. */
    public void drainBlockTicks(BiConsumer<BlockPos, Block> executor) {
        blockTicks.tick(currentTick(), MAX_SCHEDULED_TICKS_PER_DRAIN, executor);
    }

    public void drainFluidTicks(BiConsumer<BlockPos, Fluid> executor) {
        fluidTicks.tick(currentTick(), MAX_SCHEDULED_TICKS_PER_DRAIN, executor);
    }

    public void requeueBlockTick(BlockPos pos, Block block) {
        blockTicks.schedule(createTick(pos, block, 1));
    }

    public void requeueFluidTick(BlockPos pos, Fluid fluid) {
        fluidTicks.schedule(createTick(pos, fluid, 1));
    }

    /** Vanilla runBlockEvents: untickable events wait, cascades run now. */
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

    /** Two clocks: the ticks move by the offset so their delays survive. */
    public void mergeInto(RegionWorldData target) {
        long tickOffset = target.currentTick() - currentTick();
        blockTicks.mergeInto(target.blockTicks, tickOffset);
        fluidTicks.mergeInto(target.fluidTicks, tickOffset);
        target.blockEvents.addAll(blockEvents);
        blockEvents.clear();
        target.broadcastHolders.addAll(broadcastHolders);
        broadcastHolders.clear();
        blockEntityTickers.mergeInto(target.blockEntityTickers);
        target.subTick = Math.max(target.subTick, subTick);
        target.lastInhabitedUpdate = Math.max(target.lastInhabitedUpdate, lastInhabitedUpdate);
    }

    /** Activation hand-off; what has no owning region stays attached. */
    public void migrateInto(int sectionShift, LongFunction<RegionWorldData> childBySection) {
        redistribute(sectionShift, childBySection, true);
    }

    /** A dead section drops its state. */
    public void splitInto(int sectionShift, LongFunction<RegionWorldData> childBySection) {
        redistribute(sectionShift, childBySection, false);
    }

    private void redistribute(int sectionShift, LongFunction<RegionWorldData> childBySection, boolean keepOrphans) {
        blockTicks.splitInto(sectionShift, section -> {
            RegionWorldData child = childBySection.apply(section);
            return child == null ? null : child.blockTicks;
        }, keepOrphans);

        fluidTicks.splitInto(sectionShift, section -> {
            RegionWorldData child = childBySection.apply(section);
            return child == null ? null : child.fluidTicks;
        }, keepOrphans);

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

    /** Split child; the clock is the caller's. */
    public void inheritCountersFrom(RegionWorldData parent) {
        subTick = parent.subTick;
        lastInhabitedUpdate = parent.lastInhabitedUpdate;
    }
}
