package fr.hardel.leafs.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region drain over chunk containers keeps vanilla's cross-chunk order without an index. */
class ScheduledTickDrainTest {

    private record Chunk(long key, LevelChunkTicks<Block> ticks) {
    }

    private static final BlockPos WEST = new BlockPos(3, 64, 3);
    private static final BlockPos EAST = new BlockPos(19, 64, 3);

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScheduledTickDrain<Chunk, Block> drain() {
        return new ScheduledTickDrain<>(Chunk::ticks, Chunk::key);
    }

    private static Chunk chunk(long key) {
        return new Chunk(key, new LevelChunkTicks<>());
    }

    @Test
    void dueTicksRunAcrossChunksByPriorityThenSubTick() {
        Chunk west = chunk(0);
        Chunk east = chunk(1);
        west.ticks().schedule(new ScheduledTick<>(Blocks.STONE, WEST, 10, TickPriority.NORMAL, 2));
        west.ticks().schedule(new ScheduledTick<>(Blocks.STONE, WEST.above(), 10, TickPriority.HIGH, 3));
        east.ticks().schedule(new ScheduledTick<>(Blocks.STONE, EAST, 10, TickPriority.NORMAL, 1));
        east.ticks().schedule(new ScheduledTick<>(Blocks.STONE, EAST.above(), 11, 0));
        List<BlockPos> drained = new ArrayList<>();

        drain().drain(List.of(west, east), _ -> true, 10, (pos, _) -> drained.add(pos));

        assertEquals(List.of(WEST.above(), EAST, WEST), drained, "priority first, then sub-tick order, whatever the chunk");
        assertEquals(1, east.ticks().count(), "the future tick waits");
    }

    @Test
    void aChunkThatFailsTheTickCheckKeepsItsTicks() {
        Chunk west = chunk(0);
        west.ticks().schedule(new ScheduledTick<>(Blocks.STONE, WEST, 5, 0));
        List<BlockPos> drained = new ArrayList<>();

        drain().drain(List.of(west), _ -> false, 10, (pos, _) -> drained.add(pos));

        assertTrue(drained.isEmpty());
        assertEquals(1, west.ticks().count());
    }

    @Test
    void willTickThisTickAnswersDuringTheDrainOnly() {
        Chunk west = chunk(0);
        west.ticks().schedule(new ScheduledTick<>(Blocks.STONE, WEST, 5, 0));
        ScheduledTickDrain<Chunk, Block> drain = drain();
        List<Boolean> seen = new ArrayList<>();

        drain.drain(List.of(west), _ -> true, 10, (_, _) -> seen.add(drain.willTickThisTick(WEST, Blocks.STONE)));

        assertEquals(List.of(false), seen, "vanilla removes the running tick from the set before running it");
        assertFalse(drain.willTickThisTick(WEST, Blocks.STONE));
    }
}
