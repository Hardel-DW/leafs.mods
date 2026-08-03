package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * A region's scheduled-tick index. Extends the vanilla index with the container bookkeeping needed
 * to move chunks between regions: merge rebases absolute trigger ticks by the clock offset so
 * relative delays survive (two clocks), split re-buckets containers by grid section. Both only run
 * between region ticks, under the regionizer's write lock.
 */
public final class RegionScheduledTicks<T> extends LevelTicks<T> {
    private final Long2ObjectOpenHashMap<LevelChunkTicks<T>> containers = new Long2ObjectOpenHashMap<>();

    public RegionScheduledTicks(LongPredicate tickCheck) {
        super(tickCheck);
    }

    @Override
    public void addContainer(ChunkPos pos, @NonNull LevelChunkTicks<T> container) {
        containers.put(pos.pack(), container);
        super.addContainer(pos, container);
    }

    @Override
    public void removeContainer(ChunkPos pos) {
        containers.remove(pos.pack());
        super.removeContainer(pos);
    }

    public void mergeInto(RegionScheduledTicks<T> target, long tickOffset) {
        for (long chunkKey : containers.keySet().toLongArray()) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            LevelChunkTicks<T> container = containers.get(chunkKey);
            removeContainer(pos);
            rebase(container, tickOffset);
            target.addContainer(pos, container);
        }
    }

    /** No tick offset: split children inherit the parent's clock. */
    public void splitInto(int sectionShift, LongFunction<RegionScheduledTicks<T>> targetBySection) {
        for (long chunkKey : containers.keySet().toLongArray()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            RegionScheduledTicks<T> target = targetBySection.apply(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));
            if (target == null) {
                throw new IllegalStateException("No target index for chunk [" + chunkX + ", " + chunkZ + "] while splitting scheduled ticks");
            }

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            LevelChunkTicks<T> container = containers.get(chunkKey);
            removeContainer(pos);
            target.addContainer(pos, container);
        }
    }

    LevelChunkTicks<T> containerAt(int chunkX, int chunkZ) {
        return containers.get(ChunkPos.pack(chunkX, chunkZ));
    }

    /** Pending (still packed, delay-relative) ticks need no rebase; only the live queue holds absolute times. */
    private static <T> void rebase(LevelChunkTicks<T> container, long tickOffset) {
        if (tickOffset == 0) {
            return;
        }

        List<ScheduledTick<T>> drained = new ArrayList<>();
        for (ScheduledTick<T> tick = container.poll(); tick != null; tick = container.poll()) {
            drained.add(tick);
        }
        for (ScheduledTick<T> tick : drained) {
            container.schedule(new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick() + tickOffset, tick.priority(), tick.subTickOrder()));
        }
    }
}
