package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import fr.hardel.leafs.chunk.owner.Router;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
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

    /** The level as tick factory: a clock and an order counter the test moves by hand. */
    private static final class StampingLevel implements ScheduledTickAccess {
        private long time = 100;
        private long order;

        @Override
        public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
            return new ScheduledTick<>(type, pos, time + delay, priority, order++);
        }

        @Override
        public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
            return createTick(pos, type, delay, TickPriority.NORMAL);
        }

        @Override
        public LevelTickAccess<Block> getBlockTicks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LevelTickAccess<Fluid> getFluidTicks() {
            throw new UnsupportedOperationException();
        }
    }

    private final StampingLevel level = new StampingLevel();

    private ChunkScheduledTicks<Block> index(Router owners) {
        return new ChunkScheduledTicks<>(null, level, RegionWorldData::blockTicks, owners);
    }

    @Test
    void aScheduleReachesItsChunkContainerAndAnUnloadedPositionDrops() {
        ChunkScheduledTicks<Block> index = index((_, _, task) -> task.run());
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
        ChunkScheduledTicks<Block> index = index((_, _, task) -> task.run());
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
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 5, 0));

        assertEquals(0, container.count());
        mailed.forEach(Runnable::run);
        assertTrue(index.hasScheduledTick(POS, Blocks.STONE));
    }

    /** Mail outlives an unload and a reload of its chunk: the write lands in the container the owner has when it runs, never in the detached one. */
    @Test
    void mailReachesTheContainerLiveWhenItRuns() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> detached = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), detached);
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 5, 0));
        index.clearArea(new BoundingBox(0, 65, 0, 7, 128, 7));

        index.removeContainer(new ChunkPos(0, 0));
        LevelChunkTicks<Block> reloaded = new LevelChunkTicks<>();
        reloaded.schedule(new ScheduledTick<>(Blocks.STONE, POS.above(), 5, 0));
        index.addContainer(new ChunkPos(0, 0), reloaded);
        mailed.forEach(Runnable::run);

        assertEquals(0, detached.count());
        assertTrue(reloaded.hasScheduledTick(POS, Blocks.STONE), "the schedule reached the reloaded container");
        assertFalse(reloaded.hasScheduledTick(POS.above(), Blocks.STONE), "the clear reached the reloaded container");
    }

    /** Vanilla stamps clock and order in the same call as the insert; mailed, the owner stamps them when it runs the write, in step with its own ticks. */
    @Test
    void aMailedScheduleIsStampedWhenTheOwnerRunsIt() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(POS, Blocks.STONE, 5, TickPriority.HIGH);
        level.time = 200;
        level.order = 7;
        mailed.forEach(Runnable::run);

        ScheduledTick<Block> tick = container.peek();
        assertEquals(205, tick.triggerTick());
        assertEquals(TickPriority.HIGH, tick.priority());
        assertEquals(7, tick.subTickOrder());
    }
}
