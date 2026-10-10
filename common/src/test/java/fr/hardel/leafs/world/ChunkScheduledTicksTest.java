package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.owner.Router;
import fr.hardel.leafs.ticking.ClockedChunkTicks;
import fr.hardel.leafs.ticking.RegionTime;
import fr.hardel.leafs.ticking.TickState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
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

    private final RegionTime chunkTime = new RegionTime(100L, TickState.INITIAL);
    private long callerTime = 100L;

    private ChunkScheduledTicks<Block> index(Router owners) {
        return new ChunkScheduledTicks<>(null, () -> callerTime, RegionWorldData::blockTicks, owners);
    }

    private LevelChunkTicks<Block> container() {
        return new ClockedChunkTicks<>(chunkTime);
    }

    @Test
    void aScheduleReachesItsChunkContainerAndAnUnloadedPositionDrops() {
        ChunkScheduledTicks<Block> index = index((_, _, task) -> task.run());
        LevelChunkTicks<Block> container = container();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, 0L));
        index.schedule(new ScheduledTick<>(Blocks.STONE, new BlockPos(500, 64, 500), 105L, 0L));

        assertTrue(index.hasScheduledTick(POS, Blocks.STONE));
        assertEquals(1, index.count());
        index.removeContainer(new ChunkPos(0, 0));
        assertFalse(index.hasScheduledTick(POS, Blocks.STONE));
    }

    @Test
    void aTickWrittenInAnotherTimeKeepsItsDelayInTheTimeOfItsChunk() {
        ChunkScheduledTicks<Block> index = index((_, _, task) -> task.run());
        LevelChunkTicks<Block> container = new ClockedChunkTicks<>(new RegionTime(1000L, TickState.INITIAL));
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, 0L));

        assertEquals(1005L, container.peek().triggerTick());
    }

    @Test
    void aMailedTickKeepsItsDelayInTheTimeOfItsChunkWhenTheOwnerRunsIt() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> container = container();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, TickPriority.HIGH, 3L));
        for (int tick = 0; tick < 100; tick++) {
            chunkTime.beginTick(TickState.INITIAL);
        }

        mailed.forEach(Runnable::run);

        ScheduledTick<Block> tick = container.peek();
        assertEquals(205L, tick.triggerTick());
        assertEquals(TickPriority.HIGH, tick.priority());
        assertEquals(3L, tick.subTickOrder());
    }

    @Test
    void aCopyLandsAfterTheOriginalsInSubTickOrder() {
        ChunkScheduledTicks<Block> index = index((_, _, task) -> task.run());
        LevelChunkTicks<Block> container = container();
        index.addContainer(new ChunkPos(0, 0), container);
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, 4L));
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS.above(), 105L, 7L));

        index.copyArea(new BoundingBox(0, 0, 0, 7, 128, 7), new Vec3i(8, 0, 0));

        List<Long> copies = container.getAll().filter(tick -> tick.pos().getX() == 11).map(ScheduledTick::subTickOrder).sorted().toList();
        assertEquals(List.of(8L, 11L), copies, "sub-tick order minus the smallest plus the largest plus one");
    }

    @Test
    void aScheduleOnAChunkThisThreadDoesNotHoldGoesToTheOwner() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> container = container();
        index.addContainer(new ChunkPos(0, 0), container);

        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, 0L));

        assertEquals(0, container.count());
        mailed.forEach(Runnable::run);
        assertTrue(index.hasScheduledTick(POS, Blocks.STONE));
    }

    @Test
    void mailReachesTheContainerLiveWhenItRuns() {
        List<Runnable> mailed = new ArrayList<>();
        ChunkScheduledTicks<Block> index = index((_, _, task) -> mailed.add(task));
        LevelChunkTicks<Block> detached = container();
        index.addContainer(new ChunkPos(0, 0), detached);
        index.schedule(new ScheduledTick<>(Blocks.STONE, POS, 105L, 0L));
        index.clearArea(new BoundingBox(0, 65, 0, 7, 128, 7));

        index.removeContainer(new ChunkPos(0, 0));
        LevelChunkTicks<Block> reloaded = container();
        reloaded.schedule(new ScheduledTick<>(Blocks.STONE, POS.above(), 105L, 0L));
        index.addContainer(new ChunkPos(0, 0), reloaded);
        mailed.forEach(Runnable::run);

        assertEquals(0, detached.count());
        assertTrue(reloaded.hasScheduledTick(POS, Blocks.STONE), "the schedule reached the reloaded container");
        assertFalse(reloaded.hasScheduledTick(POS.above(), Blocks.STONE), "the clear reached the reloaded container");
    }
}
