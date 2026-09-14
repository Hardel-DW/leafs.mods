package fr.hardel.leafs.gametest;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.atomic.AtomicBoolean;

/** The server thread releasing its chunks at the end of its tick: the mail left on one of them ends up reading its neighbour, on a thread that can lock it. */
public final class BorrowReleaseTest {

    /** ATM11, 13 September 2026: the mail of a released chunk replayed on the releasing thread, read its neighbour chunk, still registered to that thread but no longer counted as held, and the server thread waited for itself. */
    @GameTest(maxTicks = 100)
    public void theMailOfAReleasedChunkReadsItsNeighbourWithoutWaitingForItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelRegions regions = LevelRegions.of(level);
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        ChunkPos a = new ChunkPos(origin.x(), origin.z() + 64);
        ChunkPos b = new ChunkPos(a.x() + 1, a.z());
        BlockPos inB = b.getBlockAt(0, 80, 8);
        AtomicBoolean sawStone = new AtomicBoolean();
        AtomicBoolean waitedForItself = new AtomicBoolean();

        level.setBlock(a.getBlockAt(15, 80, 8), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(inB, Blocks.STONE.defaultBlockState(), 3);
        Thread neighbour = new Thread(() -> DeferredWork.owner(level, DeferReason.BLOCK_WRITE, a.x(), a.z(), () -> {
            RegionBorrow borrow = RegionBorrow.current();
            if (borrow != null && !borrow.holds(regions, b.x(), b.z()) && !borrow.tryBorrowChunk(regions, b.x(), b.z())) {
                waitedForItself.set(true);
                return;
            }

            sawStone.set(level.getBlockState(inB).is(Blocks.STONE));
        }).submit(), "leafs-gametest-neighbour");
        neighbour.start();
        try {
            neighbour.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        helper.succeedWhen(() -> {
            helper.assertFalse(waitedForItself.get(), "reading " + b + " from the mail of " + a + " would wait for the reading thread itself");
            helper.assertTrue(sawStone.get(), "the mail of " + a + " read the stone placed in " + b);
        });
    }
}
