package fr.hardel.leafs.world;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.chunk.owner.Router;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ChunkScheduledTicks<T> extends LevelTicks<T> {
    private final ConcurrentLong2ObjectMap<LevelChunkTicks<T>> containers = new ConcurrentLong2ObjectMap<>();
    private final ServerLevel level;
    private final ScheduledTickAccess ticks;
    private final Function<RegionWorldData, ScheduledTickDrain<LevelChunk, T>> drainOf;
    private final Router owners;

    private interface ContainerVisit<C> {
        void visit(long chunkKey, LevelChunkTicks<C> container);
    }

    public ChunkScheduledTicks(ServerLevel level, ScheduledTickAccess ticks, Function<RegionWorldData, ScheduledTickDrain<LevelChunk, T>> drainOf, Router owners) {
        super(_ -> true);
        this.level = level;
        this.ticks = ticks;
        this.drainOf = drainOf;
        this.owners = owners;
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
        long chunkKey = ChunkPos.pack(tick.pos());
        if (containers.containsKey(chunkKey)) {
            write(chunkKey, container -> container.schedule(tick));
        }
    }

    public void schedule(BlockPos pos, T type, int delay, TickPriority priority) {
        long chunkKey = ChunkPos.pack(pos);
        if (containers.containsKey(chunkKey)) {
            write(chunkKey, container -> container.schedule(ticks.createTick(pos, type, delay, priority)));
        }
    }

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
        forEachContainerIn(area, (chunkKey, _) -> write(chunkKey, container -> container.removeIf(inside)));
        ScheduledTickDrain<LevelChunk, T> drain = activeDrain();
        if (drain != null) {
            drain.clearArea(area);
        }
    }

    @Override
    public void copyAreaFrom(@NonNull LevelTicks<T> source, @NonNull BoundingBox area, @NonNull Vec3i offset) {
        if (!(source instanceof ChunkScheduledTicks<T> chunked)) {
            super.copyAreaFrom(source, area, offset);
            return;
        }

        List<ScheduledTick<T>> collected = new ArrayList<>();
        ScheduledTickDrain<LevelChunk, T> drain = chunked.activeDrain();
        if (drain != null) {
            drain.collectInside(area, collected::add);
        }

        chunked.forEachContainerIn(area, (_, container) -> container.getAll().filter(tick -> area.isInside(tick.pos())).forEach(collected::add));
        LongSummaryStatistics subTicks = collected.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long shift = subTicks.getMax() - subTicks.getMin() + 1;
        for (ScheduledTick<T> tick : collected) {
            schedule(new ScheduledTick<>(tick.type(), tick.pos().offset(offset), tick.triggerTick(), tick.priority(), tick.subTickOrder() + shift));
        }
    }

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

    private void forEachContainerIn(BoundingBox area, ContainerVisit<T> visit) {
        int minX = SectionPos.posToSectionCoord(area.minX());
        int maxX = SectionPos.posToSectionCoord(area.maxX());
        int minZ = SectionPos.posToSectionCoord(area.minZ());
        int maxZ = SectionPos.posToSectionCoord(area.maxZ());
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                LevelChunkTicks<T> container = containers.get(chunkKey);
                if (container != null) {
                    visit.visit(chunkKey, container);
                }
            }
        }
    }

    private void write(long chunkKey, Consumer<LevelChunkTicks<T>> write) {
        owners.route(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), () -> {
            LevelChunkTicks<T> container = containers.get(chunkKey);
            if (container != null) {
                write.accept(container);
            }
        });
    }

    private ScheduledTickDrain<LevelChunk, T> activeDrain() {
        RegionWorldData data = WorldTickContext.activeFor(level);
        return data == null ? null : drainOf.apply(data);
    }
}
