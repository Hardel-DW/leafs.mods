package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.ToLongFunction;

/** Vanilla's {@code LevelTicks} drain over the chunks of one region: same cross-chunk order, same cap, no index to migrate. */
public final class ScheduledTickDrain<C, T> {
    private static final int MAX_TICKS_PER_DRAIN = 65536;
    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (left, right) -> ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(left.peek(), right.peek());

    private final Function<C, LevelChunkTicks<T>> containerOf;
    private final ToLongFunction<C> keyOf;
    private final Queue<LevelChunkTicks<T>> containersToTick = new PriorityQueue<>(CONTAINER_DRAIN_ORDER);
    private final Queue<ScheduledTick<T>> toRunThisTick = new ArrayDeque<>();
    private final List<ScheduledTick<T>> alreadyRunThisTick = new ArrayList<>();
    private final Set<ScheduledTick<?>> toRunThisTickSet = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);

    public ScheduledTickDrain(Function<C, LevelChunkTicks<T>> containerOf, ToLongFunction<C> keyOf) {
        this.containerOf = containerOf;
        this.keyOf = keyOf;
    }

    public void drain(List<C> chunks, LongPredicate tickCheck, long currentTick, BiConsumer<BlockPos, T> output) {
        collect(chunks, tickCheck, currentTick);
        while (!toRunThisTick.isEmpty()) {
            ScheduledTick<T> tick = toRunThisTick.poll();
            if (!toRunThisTickSet.isEmpty()) {
                toRunThisTickSet.remove(tick);
            }

            alreadyRunThisTick.add(tick);
            output.accept(tick.pos(), tick.type());
        }

        containersToTick.clear();
        alreadyRunThisTick.clear();
        toRunThisTickSet.clear();
    }

    public void collectInside(BoundingBox area, Consumer<ScheduledTick<T>> out) {
        alreadyRunThisTick.stream().filter(tick -> area.isInside(tick.pos())).forEach(out);
        toRunThisTick.stream().filter(tick -> area.isInside(tick.pos())).forEach(out);
    }

    public boolean willTickThisTick(BlockPos pos, T type) {
        if (toRunThisTickSet.isEmpty() && !toRunThisTick.isEmpty()) {
            toRunThisTickSet.addAll(toRunThisTick);
        }

        return toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    public void clearArea(BoundingBox area) {
        alreadyRunThisTick.removeIf(tick -> area.isInside(tick.pos()));
        toRunThisTick.removeIf(tick -> area.isInside(tick.pos()));
        toRunThisTickSet.clear();
    }

    private void collect(List<C> chunks, LongPredicate tickCheck, long currentTick) {
        for (C chunk : chunks) {
            LevelChunkTicks<T> container = containerOf.apply(chunk);
            ScheduledTick<T> next = container.peek();
            if (next != null && next.triggerTick() <= currentTick && tickCheck.test(keyOf.applyAsLong(chunk))) {
                containersToTick.add(container);
            }
        }

        LevelChunkTicks<T> top;
        while (toRunThisTick.size() < MAX_TICKS_PER_DRAIN && (top = containersToTick.poll()) != null) {
            toRunThisTick.add(top.poll());
            drainFrom(top, currentTick);
            ScheduledTick<T> next = top.peek();
            if (next != null && next.triggerTick() <= currentTick && toRunThisTick.size() < MAX_TICKS_PER_DRAIN) {
                containersToTick.add(top);
            }
        }
    }

    /** Keeps pulling from the same container while its ticks come before the best other container's head. */
    private void drainFrom(LevelChunkTicks<T> current, long currentTick) {
        LevelChunkTicks<T> nextBest = containersToTick.peek();
        ScheduledTick<T> nextFromOther = nextBest == null ? null : nextBest.peek();
        while (toRunThisTick.size() < MAX_TICKS_PER_DRAIN) {
            ScheduledTick<T> candidate = current.peek();
            if (candidate == null || candidate.triggerTick() > currentTick || (nextFromOther != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(candidate, nextFromOther) > 0)) {
                return;
            }

            toRunThisTick.add(current.poll());
        }
    }
}
