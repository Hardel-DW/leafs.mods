package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/**
 * A region's scheduled-tick index, dated on the region clock. A trigger arrives in game time, the
 * vanilla convention every caller uses, and is rebased on entry, so a tick built by hand from
 * {@code getGameTime()} lands at the right delay too. A merge rebases by the clock offset between
 * the two regions, a split re-buckets containers by grid section; both only run between region ticks.
 */
public final class RegionScheduledTicks<T> extends LevelTicks<T> {
    private final LongSupplier gameTime;
    private final LongSupplier time;

    public RegionScheduledTicks(LongPredicate tickCheck, LongSupplier gameTime, LongSupplier time) {
        super(tickCheck);
        this.gameTime = gameTime;
        this.time = time;
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        super.schedule(rebased(tick, time.getAsLong() - gameTime.getAsLong()));
    }

    public void mergeInto(RegionScheduledTicks<T> target, long tickOffset) {
        for (long chunkKey : allContainers.keySet().toLongArray()) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            LevelChunkTicks<T> container = allContainers.get(chunkKey);
            removeContainer(pos);
            rebase(container, tickOffset);
            target.addContainer(pos, container);
        }
    }

    /** No tick offset: split children inherit the parent's clock. */
    public void splitInto(int sectionShift, LongFunction<RegionScheduledTicks<T>> targetBySection) {
        for (long chunkKey : allContainers.keySet().toLongArray()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            RegionScheduledTicks<T> target = targetBySection.apply(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));
            if (target == null) {
                throw new IllegalStateException("No target index for chunk [" + chunkX + ", " + chunkZ + "] while splitting scheduled ticks");
            }

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            LevelChunkTicks<T> container = allContainers.get(chunkKey);
            removeContainer(pos);
            target.addContainer(pos, container);
        }
    }

    /** Pending (still packed, delay-relative) ticks need no rebase; only the live queue holds absolute times. */
    private static <T> void rebase(LevelChunkTicks<T> container, long tickOffset) {
        if (tickOffset == 0) {
            return;
        }

        List<ScheduledTick<T>> drained = new ArrayList<>();
        while (container.peek() != null) {
            drained.add(container.poll());
        }

        for (ScheduledTick<T> tick : drained) {
            container.schedule(rebased(tick, tickOffset));
        }
    }

    private static <T> ScheduledTick<T> rebased(ScheduledTick<T> tick, long tickOffset) {
        return tickOffset == 0 ? tick : new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick() + tickOffset, tick.priority(), tick.subTickOrder());
    }
}
