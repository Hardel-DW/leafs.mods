package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingScheduledTicksTest {
    private final RegionScheduledTicks<String> attached = new RegionScheduledTicks<>(_ -> true);
    private final RegionScheduledTicks<String> other = new RegionScheduledTicks<>(_ -> true);
    private final RoutingScheduledTicks<String> router = new RoutingScheduledTicks<>(_ -> true, attached);

    private static BlockPos blockIn(int chunkX, int chunkZ) {
        return new BlockPos(chunkX << 4, 64, chunkZ << 4);
    }

    private static ScheduledTick<String> tickAt(String type, BlockPos pos) {
        return new ScheduledTick<>(type, pos, 10, TickPriority.NORMAL, 0);
    }

    @Test
    void unroutedEverythingLandsInTheAttachedIndex() {
        router.addContainer(new ChunkPos(0, 0), new LevelChunkTicks<>());
        router.schedule(tickAt("a", blockIn(0, 0)));

        assertTrue(attached.hasScheduledTick(blockIn(0, 0), "a"));
        assertTrue(router.hasScheduledTick(blockIn(0, 0), "a"));
        assertEquals(1, router.count());
    }

    @Test
    void routeResolvesByPosition() {
        router.route(chunkKey -> (ChunkPos.getX(chunkKey) & 1) == 0 ? attached : other, () -> attached.count() + other.count());
        router.addContainer(new ChunkPos(0, 0), new LevelChunkTicks<>());
        router.addContainer(new ChunkPos(1, 0), new LevelChunkTicks<>());
        router.schedule(tickAt("even", blockIn(0, 0)));
        router.schedule(tickAt("odd", blockIn(1, 0)));

        assertTrue(attached.hasScheduledTick(blockIn(0, 0), "even"));
        assertFalse(attached.hasScheduledTick(blockIn(1, 0), "odd"));
        assertTrue(other.hasScheduledTick(blockIn(1, 0), "odd"));
        assertTrue(router.hasScheduledTick(blockIn(0, 0), "even"));
        assertTrue(router.hasScheduledTick(blockIn(1, 0), "odd"));
        assertEquals(2, router.count());
    }

    @Test
    void clearAreaReachesEveryResolvedIndex() {
        router.route(chunkKey -> (ChunkPos.getX(chunkKey) & 1) == 0 ? attached : other, () -> attached.count() + other.count());
        router.addContainer(new ChunkPos(0, 0), new LevelChunkTicks<>());
        router.addContainer(new ChunkPos(1, 0), new LevelChunkTicks<>());
        router.schedule(tickAt("even", blockIn(0, 0)));
        router.schedule(tickAt("odd", blockIn(1, 0)));

        router.clearArea(new BoundingBox(0, 0, 0, 31, 128, 15));

        assertEquals(0, router.count());
    }

    @Test
    void copyAreaFromUnwrapsARouterSource() {
        RegionScheduledTicks<String> sourceAttached = new RegionScheduledTicks<>(_ -> true);
        RoutingScheduledTicks<String> sourceRouter = new RoutingScheduledTicks<>(_ -> true, sourceAttached);
        sourceRouter.addContainer(new ChunkPos(0, 0), new LevelChunkTicks<>());
        sourceRouter.schedule(tickAt("copied", blockIn(0, 0)));
        router.addContainer(new ChunkPos(1, 0), new LevelChunkTicks<>());

        router.copyAreaFrom(sourceRouter, new BoundingBox(0, 0, 0, 15, 128, 15), new Vec3i(16, 0, 0));

        assertTrue(attached.hasScheduledTick(blockIn(1, 0), "copied"));
    }
}
