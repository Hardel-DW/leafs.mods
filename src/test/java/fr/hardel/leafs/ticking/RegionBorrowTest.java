package fr.hardel.leafs.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBorrowTest {
    private final LevelRegions regions = new LevelRegions(new LeafsConfig(LeafsConfig.ALL_CORES, LeafsConfig.ALL_CORES, 16, 1, 1, LeafsConfig.defaults().debug(), LeafsConfig.defaults().gameplay()));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void exitBorrow() {
        RegionBorrow.exit();
    }

    @Test
    void aBorrowedRegionTicksForNobodyElseUntilReleased() {
        simulated(regions, 0, 0);
        Region<RegionTickData> region = regions.regionizer().regionAt(0, 0);
        RegionBorrow borrow = RegionBorrow.enter();

        borrow.borrow(regions, 0, 0);
        borrow.borrow(regions, 0, 0);

        assertEquals(1, borrow.size(), "the same region is taken once");
        assertEquals(RegionState.TICKING, region.state());
        assertFalse(region.tryMarkTicking(), "a worker only tries, and fails while the region is borrowed");

        borrow.releaseAll();
        assertEquals(RegionState.READY, region.state());
        assertTrue(region.tryMarkTicking());
        region.markNotTicking();
    }

    @Test
    void aBorrowWaitsForTheTickInFlightAndNothingElse() throws InterruptedException {
        simulated(regions, 0, 0);
        simulated(regions, 200, 200);
        Region<RegionTickData> ticking = regions.regionizer().regionAt(0, 0);
        assertTrue(ticking.tryMarkTicking());
        CountDownLatch borrowed = new CountDownLatch(1);
        AtomicBoolean tookTheOther = new AtomicBoolean();
        Thread borrower = new Thread(() -> {
            RegionBorrow borrow = RegionBorrow.enter();
            borrow.borrow(regions, 200, 200);
            tookTheOther.set(borrow.size() == 1);
            borrow.borrow(regions, 0, 0);
            borrowed.countDown();
        });
        borrower.start();

        assertFalse(borrowed.await(100, TimeUnit.MILLISECONDS), "the region in flight is not taken before its tick ends");
        assertTrue(tookTheOther.get(), "the idle region was taken without waiting");

        ticking.markNotTicking();
        assertTrue(borrowed.await(2, TimeUnit.SECONDS));
        assertEquals(RegionState.TICKING, ticking.state());
        borrower.join();
    }

    /** A merge between a held region and the one being taken can only run once the held one is returned; the borrower returns everything and takes the survivor. */
    @Test
    void aPendingMergeWithAHeldRegionIsFoldedByReleasingAndTakingTheSurvivor() {
        simulated(regions, 0, 0);
        simulated(regions, 96, 0);
        regions.settle();
        Region<RegionTickData> west = regions.regionizer().regionAt(0, 0);
        Region<RegionTickData> east = regions.regionizer().regionAt(96, 0);
        assertNotSame(west, east);
        RegionBorrow borrow = RegionBorrow.enter();
        borrow.borrow(regions, 0, 0);

        simulated(regions, 32, 0);
        simulated(regions, 64, 0);
        borrow.borrow(regions, 96, 0);

        Region<RegionTickData> survivor = regions.regionizer().regionAt(96, 0);
        assertSame(survivor, regions.regionizer().regionAt(0, 0), "the merge ran");
        assertEquals(RegionState.TICKING, survivor.state());
        assertEquals(1, borrow.size());
        borrow.releaseAll();
        assertEquals(RegionState.READY, survivor.state());
    }

    /** The same fold for the whole level: a region owed to one this thread holds cannot be taken until the held one is returned. */
    @Test
    void borrowAllFoldsAPendingMergeWithAHeldRegion() throws InterruptedException {
        simulated(regions, 0, 0);
        simulated(regions, 96, 0);
        regions.settle();
        CountDownLatch done = new CountDownLatch(1);
        Thread borrower = new Thread(() -> {
            RegionBorrow borrow = RegionBorrow.enter();
            borrow.borrow(regions, 0, 0);
            simulated(regions, 32, 0);
            simulated(regions, 64, 0);
            borrow.borrowAll(regions);
            done.countDown();
        });
        borrower.start();

        assertTrue(done.await(2, TimeUnit.SECONDS), "borrowAll must return everything, let the merge run and take the survivor");
        Region<RegionTickData> survivor = regions.regionizer().regionAt(96, 0);
        assertSame(survivor, regions.regionizer().regionAt(0, 0), "the merge ran");
        assertEquals(RegionState.TICKING, survivor.state());
        borrower.join();
    }

    @Test
    void borrowAllTakesEveryLiveRegion() {
        simulated(regions, 0, 0);
        simulated(regions, 200, 200);
        simulated(regions, -200, -200);
        RegionBorrow borrow = RegionBorrow.enter();

        borrow.borrowAll(regions);

        assertEquals(3, borrow.size());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertEquals(RegionState.TICKING, region.state());
        }
    }

    private static void simulated(LevelRegions regions, int chunkX, int chunkZ) {
        regions.changed(ChunkPos.pack(chunkX, chunkZ), ChunkLevel.MAX_LEVEL + 1, ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING));
    }

}
