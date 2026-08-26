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

/** The vanilla index on the region clock. Triggers come in game time, like vanilla, and are rebased at the door. */
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

    /** A container without a target sits in a dead section: its chunk is unloading, the index lets it go unless the caller keeps strays. */
    public void splitInto(int sectionShift, LongFunction<RegionScheduledTicks<T>> targetBySection, boolean keepOrphans) {
        for (long chunkKey : allContainers.keySet().toLongArray()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            RegionScheduledTicks<T> target = targetBySection.apply(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));
            if (target == null && keepOrphans) {
                continue;
            }

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            LevelChunkTicks<T> container = allContainers.get(chunkKey);
            removeContainer(pos);
            if (target != null) {
                target.addContainer(pos, container);
            }
        }
    }

    /** Packed ticks are delay-relative already, only the live queue moves. */
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
