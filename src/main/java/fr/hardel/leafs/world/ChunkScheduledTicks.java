package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** The level's tick field: a concurrent map of the chunk containers, nothing else. The drain belongs to the owning region, see {@link ScheduledTickDrain}. */
public final class ChunkScheduledTicks<T> extends LevelTicks<T> {
    private final ConcurrentHashMap<Long, LevelChunkTicks<T>> containers = new ConcurrentHashMap<>();
    private final ServerLevel level;
    private final Function<RegionWorldData, ScheduledTickDrain<LevelChunk, T>> drainOf;

    public ChunkScheduledTicks(ServerLevel level, Function<RegionWorldData, ScheduledTickDrain<LevelChunk, T>> drainOf) {
        super(_ -> true);
        this.level = level;
        this.drainOf = drainOf;
    }

    @Override
    public void addContainer(ChunkPos pos, @NonNull LevelChunkTicks<T> container) {
        containers.put(pos.pack(), container);
    }

    @Override
    public void removeContainer(ChunkPos pos) {
        containers.remove(pos.pack());
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        LevelChunkTicks<T> container = containers.get(ChunkPos.pack(tick.pos()));
        if (container != null) {
            container.schedule(tick);
        }
    }

    /** The serial level tick calls this; every chunk is drained by its region instead. */
    @Override
    public void tick(long currentTick, int maxTicksToProcess, @NonNull BiConsumer<BlockPos, T> output) {
    }

    @Override
    public boolean hasScheduledTick(@NonNull BlockPos pos, @NonNull T type) {
        LevelChunkTicks<T> container = containers.get(ChunkPos.pack(pos));
        return container != null && container.hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(@NonNull BlockPos pos, @NonNull T type) {
        ScheduledTickDrain<LevelChunk, T> drain = activeDrain();
        return drain != null && drain.willTickThisTick(pos, type);
    }

    @Override
    public void clearArea(@NonNull BoundingBox area) {
        Predicate<ScheduledTick<T>> inside = tick -> area.isInside(tick.pos());
        for (LevelChunkTicks<T> container : containersIn(area)) {
            container.removeIf(inside);
        }

        ScheduledTickDrain<LevelChunk, T> drain = activeDrain();
        if (drain != null) {
            drain.clearArea(area);
        }
    }

    @Override
    public void copyArea(@NonNull BoundingBox area, @NonNull Vec3i offset) {
        copyAreaFrom(this, area, offset);
    }

    /** A foreign source keeps vanilla's walk of its own containers; ours has no such walk, so the area is collected here. */
    @Override
    public void copyAreaFrom(@NonNull LevelTicks<T> source, @NonNull BoundingBox area, @NonNull Vec3i offset) {
        if (!(source instanceof ChunkScheduledTicks<T> chunked)) {
            super.copyAreaFrom(source, area, offset);
            return;
        }

        List<ScheduledTick<T>> collected = new ArrayList<>();
        for (LevelChunkTicks<T> container : chunked.containersIn(area)) {
            container.getAll().filter(tick -> area.isInside(tick.pos())).forEach(collected::add);
        }

        for (ScheduledTick<T> tick : collected) {
            schedule(new ScheduledTick<>(tick.type(), tick.pos().offset(offset), tick.triggerTick(), tick.priority(), tick.subTickOrder()));
        }
    }

    /** A chunk changing owner carries its ticks in the old owner's time; the live queues move by the clock difference, packed ticks are delay-relative already. */
    public static void rebase(LevelChunk chunk, long tickOffset) {
        rebase(chunk.blockTicks, tickOffset);
        rebase(chunk.fluidTicks, tickOffset);
    }

    public static <T> void rebase(LevelChunkTicks<T> container, long tickOffset) {
        List<ScheduledTick<T>> drained = new ArrayList<>();
        while (container.peek() != null) {
            drained.add(container.poll());
        }

        for (ScheduledTick<T> tick : drained) {
            container.schedule(new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick() + tickOffset, tick.priority(), tick.subTickOrder()));
        }
    }

    @Override
    public int count() {
        int total = 0;
        for (LevelChunkTicks<T> container : containers.values()) {
            total += container.count();
        }

        return total;
    }

    private List<LevelChunkTicks<T>> containersIn(BoundingBox area) {
        List<LevelChunkTicks<T>> found = new ArrayList<>();
        int minX = SectionPos.posToSectionCoord(area.minX());
        int maxX = SectionPos.posToSectionCoord(area.maxX());
        int minZ = SectionPos.posToSectionCoord(area.minZ());
        int maxZ = SectionPos.posToSectionCoord(area.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LevelChunkTicks<T> container = containers.get(ChunkPos.pack(x, z));
                if (container != null) {
                    found.add(container);
                }
            }
        }

        return found;
    }

    private ScheduledTickDrain<LevelChunk, T> activeDrain() {
        RegionWorldData data = WorldTickContext.activeFor(level);
        return data == null ? null : drainOf.apply(data);
    }
}
