package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionScheduledTicksTest {
    private static final int SECTION_SHIFT = 4;

    private final RegionScheduledTicks<String> ticks = new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L);
    private final List<String> drained = new ArrayList<>();

    private LevelChunkTicks<String> newContainer(int chunkX, int chunkZ) {
        LevelChunkTicks<String> container = new LevelChunkTicks<>();
        ticks.addContainer(new ChunkPos(chunkX, chunkZ), container);

        return container;
    }

    private static BlockPos blockIn(int chunkX, int chunkZ, int offset) {
        return new BlockPos((chunkX << 4) + offset, 64, chunkZ << 4);
    }

    private void drainAt(RegionScheduledTicks<String> index, long time) {
        index.tick(time, 65536, (pos, type) -> drained.add(type));
    }

    @Test
    void drainFollowsTriggerTickThenPriorityThenSubTick() {
        newContainer(0, 0);
        newContainer(1, 0);
        ticks.schedule(new ScheduledTick<>("late", blockIn(0, 0, 0), 20, TickPriority.NORMAL, 0));
        ticks.schedule(new ScheduledTick<>("early-high", blockIn(1, 0, 0), 10, TickPriority.HIGH, 3));
        ticks.schedule(new ScheduledTick<>("early-first", blockIn(0, 0, 1), 10, TickPriority.NORMAL, 1));
        ticks.schedule(new ScheduledTick<>("early-second", blockIn(1, 0, 1), 10, TickPriority.NORMAL, 2));

        drainAt(ticks, 15);

        assertEquals(List.of("early-high", "early-first", "early-second"), drained);
        drained.clear();
        drainAt(ticks, 20);
        assertEquals(List.of("late"), drained);
    }

    @Test
    void drainCapLeavesOverflowForTheNextDrain() {
        newContainer(0, 0);
        ticks.schedule(new ScheduledTick<>("first", blockIn(0, 0, 0), 10, TickPriority.NORMAL, 0));
        ticks.schedule(new ScheduledTick<>("second", blockIn(0, 0, 1), 10, TickPriority.NORMAL, 1));
        ticks.schedule(new ScheduledTick<>("third", blockIn(0, 0, 2), 10, TickPriority.NORMAL, 2));

        ticks.tick(10, 2, (pos, type) -> drained.add(type));
        assertEquals(List.of("first", "second"), drained);

        drained.clear();
        ticks.tick(10, 2, (pos, type) -> drained.add(type));
        assertEquals(List.of("third"), drained);
    }

    @Test
    void mergeRebasesAbsoluteTriggerTicksByTheClockOffset() {
        newContainer(0, 0);
        ticks.schedule(new ScheduledTick<>("moved", blockIn(0, 0, 0), 105, TickPriority.NORMAL, 0));

        RegionScheduledTicks<String> target = new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L);
        LevelChunkTicks<String> targetContainer = new LevelChunkTicks<>();
        target.addContainer(new ChunkPos(2, 0), targetContainer);
        target.schedule(new ScheduledTick<>("resident", blockIn(2, 0, 0), 1004, TickPriority.NORMAL, 1));

        ticks.mergeInto(target, 900);

        drainAt(target, 1004);
        assertEquals(List.of("resident"), drained, "the moved tick keeps its 5-tick remaining delay (was due at 105 on a clock at 100)");
        drained.clear();
        drainAt(target, 1005);
        assertEquals(List.of("moved"), drained);
        drainAt(ticks, Long.MAX_VALUE - 1);
        assertEquals(List.of("moved"), drained, "the source index must be empty after the merge");
    }

    @Test
    void mergedContainerKeepsReceivingSchedules() {
        newContainer(0, 0);
        RegionScheduledTicks<String> target = new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L);

        ticks.mergeInto(target, 0);
        target.schedule(new ScheduledTick<>("after-merge", blockIn(0, 0, 2), 30, TickPriority.NORMAL, 0));

        drainAt(target, 30);
        assertEquals(List.of("after-merge"), drained);
    }

    @Test
    void splitRebucketsContainersBySectionWithoutOffset() {
        newContainer(0, 0);
        newContainer(17, 0);
        ticks.schedule(new ScheduledTick<>("west", blockIn(0, 0, 0), 50, TickPriority.NORMAL, 0));
        ticks.schedule(new ScheduledTick<>("east", blockIn(17, 0, 0), 50, TickPriority.NORMAL, 1));

        Long2ObjectMap<RegionScheduledTicks<String>> children = new Long2ObjectOpenHashMap<>();
        children.put(CoordinateKey.pack(0, 0), new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L));
        children.put(CoordinateKey.pack(1, 0), new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L));

        ticks.splitInto(SECTION_SHIFT, children::get, false);

        drainAt(children.get(CoordinateKey.pack(0, 0)), 50);
        assertEquals(List.of("west"), drained);
        drained.clear();
        drainAt(children.get(CoordinateKey.pack(1, 0)), 50);
        assertEquals(List.of("east"), drained);
    }

    /** The 2026-08-26 crash: a chunk unloading out of a dead section still had its container at the split. */
    @Test
    void splitDropsTheContainerOfADeadSectionAndKeepsStraysWhenAsked() {
        newContainer(0, 0);
        ticks.schedule(new ScheduledTick<>("dying", blockIn(0, 0, 0), 5, TickPriority.NORMAL, 0));

        ticks.splitInto(SECTION_SHIFT, _ -> null, true);
        assertEquals(1, ticks.count(), "activation keeps what has no owner yet");

        ticks.splitInto(SECTION_SHIFT, _ -> null, false);
        assertEquals(0, ticks.count(), "a split lets the dead section's container go");
        ticks.removeContainer(new ChunkPos(0, 0));
    }

    @Test
    void pendingPackedTicksSurviveAMergeUntouched() {
        LevelChunkTicks<String> loaded = new LevelChunkTicks<>(List.of(new SavedTick<>("pending", blockIn(0, 0, 3), 7, TickPriority.NORMAL)));
        ticks.addContainer(new ChunkPos(0, 0), loaded);

        RegionScheduledTicks<String> target = new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L);
        ticks.mergeInto(target, 900);
        loaded.unpack(1000);

        drainAt(target, 1006);
        assertEquals(List.of(), drained, "a 7-tick delay unpacked at 1000 is due at 1007");
        drainAt(target, 1007);
        assertEquals(List.of("pending"), drained);
    }

    @Test
    void packAfterMergeSavesRegionRelativeDelays() {
        newContainer(0, 0);
        ticks.schedule(new ScheduledTick<>("saved", blockIn(0, 0, 0), 110, TickPriority.NORMAL, 0));
        RegionScheduledTicks<String> target = new RegionScheduledTicks<>(_ -> true, () -> 0L, () -> 0L);

        ticks.mergeInto(target, 900);

        List<SavedTick<String>> saved = target.allContainers.get(ChunkPos.pack(0, 0)).pack(1005);
        assertEquals(1, saved.size());
        assertEquals(5, saved.getFirst().delay(), "10 ticks remaining at merge, 5 elapsed on the new clock");
    }
}
