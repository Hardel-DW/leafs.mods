package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.BiConsumer;

/**
 * The level's scheduled-tick field: every position-keyed operation resolves to the owning index.
 * {@link #route} is the single flip switch; until it is called everything resolves to the attached
 * index, which is the vanilla shape. The copy-area family always targets the attached index: its
 * vanilla implementation reads private state that cannot be reached across instances.
 */
public final class RoutingScheduledTicks<T> extends LevelTicks<T> {
    private final RegionScheduledTicks<T> attached;
    private LongFunction<RegionScheduledTicks<T>> resolver;
    private IntSupplier counter;

    public RoutingScheduledTicks(LongPredicate tickCheck, RegionScheduledTicks<T> attached) {
        super(tickCheck);
        this.attached = attached;
        this.resolver = chunkKey -> attached;
        this.counter = attached::count;
    }

    public void route(LongFunction<RegionScheduledTicks<T>> resolver, IntSupplier counter) {
        this.resolver = resolver;
        this.counter = counter;
    }

    @Override
    public void addContainer(ChunkPos pos, @NonNull LevelChunkTicks<T> container) {
        resolver.apply(pos.pack()).addContainer(pos, container);
    }

    @Override
    public void removeContainer(ChunkPos pos) {
        resolver.apply(pos.pack()).removeContainer(pos);
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        resolver.apply(ChunkPos.pack(tick.pos())).schedule(tick);
    }

    @Override
    public void tick(long currentTick, int maxTicksToProcess, BiConsumer<BlockPos, T> output) {
        attached.tick(currentTick, maxTicksToProcess, output);
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return resolver.apply(ChunkPos.pack(pos)).hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        return resolver.apply(ChunkPos.pack(pos)).willTickThisTick(pos, type);
    }

    @Override
    public void clearArea(BoundingBox area) {
        for (RegionScheduledTicks<T> index : indexesIn(area)) {
            index.clearArea(area);
        }
    }

    @Override
    public void copyArea(BoundingBox area, Vec3i offset) {
        attached.copyArea(area, offset);
    }

    @Override
    public void copyAreaFrom(LevelTicks<T> source, BoundingBox area, Vec3i offset) {
        attached.copyAreaFrom(source instanceof RoutingScheduledTicks<T> routing ? routing.attached : source, area, offset);
    }

    @Override
    public int count() {
        return counter.getAsInt();
    }

    private Set<RegionScheduledTicks<T>> indexesIn(BoundingBox area) {
        Set<RegionScheduledTicks<T>> indexes = new HashSet<>();
        int minX = SectionPos.posToSectionCoord(area.minX());
        int maxX = SectionPos.posToSectionCoord(area.maxX());
        int minZ = SectionPos.posToSectionCoord(area.minZ());
        int maxZ = SectionPos.posToSectionCoord(area.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                indexes.add(resolver.apply(ChunkPos.pack(x, z)));
            }
        }

        return indexes;
    }
}
