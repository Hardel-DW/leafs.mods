package fr.hardel.leafs.ticking;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.RegionizerAssertions;
import fr.hardel.leafs.world.RegionWorldData;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class LevelRegionsTest {
    private LevelRegions regions;

    @BeforeEach
    void createRegions() {
        regions = new LevelRegions(new LeafsConfig(LeafsConfig.ALL_CORES, LeafsConfig.ALL_CORES, 16, 1, 1, LeafsConfig.defaults().debug(), LeafsConfig.defaults().gameplay()));
    }

    @Test
    void aBridgeOnlySplitsOnSettleAndMergesBackOnRefill() {
        simulated(regions, 0, 0);
        simulated(regions, 32, 0);
        simulated(regions, 64, 0);
        simulated(regions, 96, 0);
        settle();
        assertEquals(1, regionCount());
        assertEquals(1, regions.created());

        unsimulated(regions, 32, 0);
        unsimulated(regions, 64, 0);

        assertEquals(1, regionCount(), "the feed alone never splits, the end of a tick does");
        assertEquals(0, regions.split());

        settle();

        assertEquals(2, regionCount());
        assertEquals(1, regions.split());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertEquals(RegionState.READY, region.state());
        }
        assertNotSame(regions.regionizer().regionAt(0, 0), regions.regionizer().regionAt(96, 0));

        simulated(regions, 32, 0);
        simulated(regions, 64, 0);
        settle();

        assertEquals(1, regionCount());
        assertEquals(1, regions.merged());
        assertSame(regions.regionizer().regionAt(0, 0), regions.regionizer().regionAt(96, 0));
        RegionizerAssertions.assertInvariants(regions.regionizer(), true);
    }

    @Test
    void drainingEveryChunkReclaimsSectionsAndRegions() {
        simulated(regions, 0, 0);
        simulated(regions, 1, 0);
        simulated(regions, 200, 200);
        settle();
        assertEquals(2, regionCount());
        assertTrue(regions.sections() > 0);

        unsimulated(regions, 0, 0);
        unsimulated(regions, 1, 0);
        unsimulated(regions, 200, 200);
        settle();

        assertEquals(0, regionCount());
        assertEquals(0, regions.sections());
        assertEquals(0, regions.deadSections());
    }

    @Test
    void activationBindsHandlesAndRegionDeathCancelsThem() {
        simulated(regions, 0, 0);
        simulated(regions, 200, 200);
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNull(region.data().handle(), "no handle may exist before the pool binds");
        }

        activateRegions();
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNotNull(region.data().handle(), "activation must bind every live region");
            assertNotNull(region.data().worldData(), "activation must attach the world payload to every region");
        }

        RegionTickHandle doomed = regions.regionizer().regionAt(200, 200).data().handle();
        unsimulated(regions, 200, 200);
        settle();
        assertTrue(doomed.isCancelled());
        assertEquals(1, regionCount());
    }

    @Test
    void aReleaseSplitsAndTheChildrenCarryFreshHandles() {
        activateRegions();
        simulated(regions, 0, 0);
        simulated(regions, 32, 0);
        simulated(regions, 64, 0);
        simulated(regions, 96, 0);
        RegionTickHandle parentHandle = regions.regionizer().regionAt(0, 0).data().handle();

        unsimulated(regions, 32, 0);
        unsimulated(regions, 64, 0);

        settle();
        assertEquals(2, regionCount(), "the release is what splits");
        assertEquals(1, regions.split());
        assertTrue(parentHandle.isCancelled());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNotNull(region.data().handle());
            assertFalse(region.data().handle().isCancelled());
        }
    }

    @Test
    void mergeKeepsTheSurvivorTimeAndSplitChildrenStartOnTheParentTime() {
        activateRegions();
        simulated(regions, 0, 0);
        simulated(regions, 96, 0);
        settle();
        assertEquals(2, regionCount());
        RegionTime west = timeAt(0);
        RegionTime east = timeAt(96);
        west.beginTick(TickState.INITIAL);
        west.beginTick(TickState.INITIAL);
        east.beginTick(TickState.INITIAL);

        simulated(regions, 32, 0);
        simulated(regions, 64, 0);
        settle();
        assertEquals(1, regionCount());
        RegionTime survivor = timeAt(0);
        assertTrue(survivor == west || survivor == east, "the survivor keeps one of the two times untouched");

        survivor.beginTick(TickState.INITIAL);
        unsimulated(regions, 32, 0);
        unsimulated(regions, 64, 0);
        settle();
        assertEquals(2, regionCount());
        assertEquals(survivor.currentTick(), timeAt(0).currentTick());
        assertEquals(survivor.currentTick(), timeAt(96).currentTick());
    }

    private RegionTime timeAt(int chunkX) {
        return regions.regionizer().regionAt(chunkX, 0).data().worldData().time();
    }

    private void activateRegions() {
        LeafsWatchdog watchdog = new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), _ -> {
        }, _ -> {
        });
        RegionTickScheduler scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), new TickEpochs(1, () -> { }), false, watchdog, (_, _) -> {
        });
        regions.activate("leafs:test", scheduler, () -> 50_000_000L, () -> new RegionWorldData(new RegionTime(0L, TickState.INITIAL), RandomSource.create(0L), null, new PathTypeCache()),
            null);
    }

    private void settle() {
        RegionBorrow borrow = RegionBorrow.enter();
        borrow.borrowAll(regions);
        borrow.releaseAll();
        borrow.exit();
    }

    private int regionCount() {
        return regions.regionizer().regionsView().size();
    }

    private static void simulated(LevelRegions regions, int chunkX, int chunkZ) {
        regions.changed(ChunkPos.pack(chunkX, chunkZ), ChunkLevel.MAX_LEVEL + 1, ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING));
    }

    private static void unsimulated(LevelRegions regions, int chunkX, int chunkZ) {
        regions.changed(ChunkPos.pack(chunkX, chunkZ), ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING), ChunkLevel.MAX_LEVEL + 1);
    }
}
