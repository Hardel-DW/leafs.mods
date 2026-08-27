package fr.hardel.leafs.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkScheduledTicksTest {
    private static final BlockPos POS = new BlockPos(3, 64, 3);

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aScheduleReachesItsChunkContainerAndAnUnloadedPositionDrops() {
        ChunkScheduledTicks<Block> index = new ChunkScheduledTicks<>(null, RegionWorldData::blockTicks);
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 5, 0));
        index.schedule(new ScheduledTick<>(Blocks.STONE, new BlockPos(500, 64, 500), 5, 0));

        assertTrue(index.hasScheduledTick(POS, Blocks.STONE));
        assertEquals(1, index.count());
        index.removeContainer(new ChunkPos(0, 0));
        assertFalse(index.hasScheduledTick(POS, Blocks.STONE));
    }

    /** A merge moves a chunk onto the survivor's clock: its live ticks keep their delay, not their absolute trigger. */
    @Test
    void rebaseShiftsTheLiveQueueByTheClockDifference() {
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        container.schedule(new ScheduledTick<>(Blocks.STONE, POS, 103, 0));
        container.schedule(new ScheduledTick<>(Blocks.STONE, POS.above(), 110, 1));

        ChunkScheduledTicks.rebase(container, 900);

        assertEquals(1003, container.peek().triggerTick());
        assertEquals(2, container.count());
        assertTrue(container.hasScheduledTick(POS.above(), Blocks.STONE));
    }
}
