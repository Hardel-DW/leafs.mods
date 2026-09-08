package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class ChunkScheduledTicksTest {
    private static final BlockPos POS = new BlockPos(3, 64, 3);

    @Test
    void aScheduleReachesItsChunkContainerAndAnUnloadedPositionDrops() {
        ChunkScheduledTicks<Block> index = new ChunkScheduledTicks<>(null, RegionWorldData::blockTicks, (_, _, task) -> task.run());
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

    /** Vanilla shifts the copies past the originals, so a clone ticks after what it was cloned from. */
    @Test
    void aCopyLandsAfterTheOriginalsInSubTickOrder() {
        ChunkScheduledTicks<Block> index = new ChunkScheduledTicks<>(null, RegionWorldData::blockTicks, (_, _, task) -> task.run());
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), container);
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 5, 4));
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS.above(), 5, 7));

        index.copyArea(new BoundingBox(0, 0, 0, 7, 128, 7), new Vec3i(8, 0, 0));

        List<Long> copies = container.getAll().filter(tick -> tick.pos().getX() == 11).map(ScheduledTick::subTickOrder).sorted().toList();
        assertEquals(List.of(8L, 11L), copies, "sub-tick order minus the smallest plus the largest plus one");
    }

    /** A tick is a write into the chunk: a thread that does not hold it hands the write to the owner instead of touching the queue. */
    @Test
    void aScheduleOnAChunkThisThreadDoesNotHoldGoesToTheOwner() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = new ChunkScheduledTicks<>(null, RegionWorldData::blockTicks, (_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 5, 0));

        assertEquals(0, container.count());
        mailed.forEach(Runnable::run);
        assertTrue(index.hasScheduledTick(POS, Blocks.STONE));
    }
}
