package fr.hardel.leafs.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.RegionizerAssertions;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives LevelRegions like the holder feed: alternating create/destroy per position, one settle per tick. */
class LevelRegionsTest {
    private static final int FEED_EVENTS = 100_000;
    private static final int EVENTS_PER_TICK = 50;
    private static final int PHASE_EVENTS = 5_000;
    private static final int POSITION_STRIDE = 8;
    private static final int POSITIONS_PER_AXIS = 24;
    private static final int POSITION_COUNT = POSITIONS_PER_AXIS * POSITIONS_PER_AXIS;

    private LevelRegions regions;

    @BeforeEach
    void createRegions() {
        regions = new LevelRegions(LeafsConfig.defaults());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 42, 20260801})
    void feedReplayKeepsEveryInvariantAndDrainsToNothing(long seed) {
        Random random = new Random(seed);
        LongOpenHashSet present = new LongOpenHashSet();
        LongArrayList presentList = new LongArrayList();

        for (int event = 0; event < FEED_EVENTS; event++) {
            /* Alternating grow and shrink phases: a purely random walk settles at half density, where the
               whole area stays one region for ever and neither split nor reclaim is ever exercised. */
            int createChance = (event / PHASE_EVENTS) % 2 == 0 ? 70 : 30;
            if (presentList.isEmpty() || (presentList.size() < POSITION_COUNT && random.nextInt(100) < createChance)) {
                feedCreate(random, present, presentList);
            } else {
                feedDestroy(random, present, presentList);
            }

            if (event % EVENTS_PER_TICK == EVENTS_PER_TICK - 1) {
                regions.settle();
                RegionizerAssertions.assertInvariants(regions.regionizer(), true);
                assertEquals(0, regions.deferredHandshakes());
            }
        }

        while (!presentList.isEmpty()) {
            feedDestroy(random, present, presentList);
        }
        regions.settle();

        assertEquals(0, regionCount(), "regions survived an empty chunk-holder map");
        assertEquals(0, regions.sections());
        assertEquals(0, regions.deferredHandshakes());
        assertTrue(regions.created() > 1, "the replay never created a second region");
        assertTrue(regions.merged() > 0, "the replay never merged two regions");
        assertTrue(regions.split() > 0, "the replay never split a region");
    }

    @Test
    void aBridgeOnlySplitsOnSettleAndMergesBackOnRefill() {
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(32, 0);
        regions.chunkHolderCreated(64, 0);
        regions.chunkHolderCreated(96, 0);
        regions.settle();
        assertEquals(1, regionCount());
        assertEquals(1, regions.created());

        regions.chunkHolderDestroyed(32, 0);
        regions.chunkHolderDestroyed(64, 0);

        assertEquals(1, regionCount(), "splitting is what settle() exists for - the feed alone never splits");
        assertEquals(0, regions.split());

        regions.settle();

        assertEquals(2, regionCount());
        assertEquals(1, regions.split());
        assertEquals(0, regions.deferredHandshakes());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertEquals(RegionState.READY, region.state());
        }
        assertNotSame(regions.regionizer().regionAt(0, 0), regions.regionizer().regionAt(96, 0));

        regions.chunkHolderCreated(32, 0);
        regions.chunkHolderCreated(64, 0);
        regions.settle();

        assertEquals(1, regionCount());
        assertEquals(1, regions.merged());
        assertSame(regions.regionizer().regionAt(0, 0), regions.regionizer().regionAt(96, 0));
        RegionizerAssertions.assertInvariants(regions.regionizer(), true);
    }

    @Test
    void drainingEveryChunkReclaimsSectionsAndRegions() {
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(1, 0);
        regions.chunkHolderCreated(200, 200);
        regions.settle();
        assertEquals(2, regionCount());
        assertTrue(regions.sections() > 0);

        regions.chunkHolderDestroyed(0, 0);
        regions.chunkHolderDestroyed(1, 0);
        regions.chunkHolderDestroyed(200, 200);
        regions.settle();

        assertEquals(0, regionCount());
        assertEquals(0, regions.sections());
        assertEquals(0, regions.deadSections());
        assertEquals(0, regions.deferredHandshakes());
    }

    @Test
    void aFeedFailureIsRethrownOnceByTheNextSettle() {
        regions.chunkHolderCreated(0, 0);

        assertThrows(IllegalStateException.class, () -> regions.chunkHolderDestroyed(500, 500));
        assertThrows(IllegalStateException.class, regions::settle, "the recorded failure must reach the game thread");

        regions.settle();
        assertEquals(1, regionCount());
    }

    @Test
    void activationBindsHandlesAndRegionDeathCancelsThem() {
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(200, 200);
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNull(region.data().handle(), "no handle may exist before the pool binds");
        }

        activateRegions();
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNotNull(region.data().handle(), "activation must bind every live region");
            assertNotNull(region.data().worldData(), "activation must attach the world payload to every region");
        }

        RegionTickHandle doomed = regions.regionizer().regionAt(200, 200).data().handle();
        regions.chunkHolderDestroyed(200, 200);
        regions.settle();
        assertTrue(doomed.isCancelled());
        assertEquals(1, regionCount());
    }

    @Test
    void aHandleTickSplitsAndItsChildrenCarryFreshHandles() {
        activateRegions();
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(32, 0);
        regions.chunkHolderCreated(64, 0);
        regions.chunkHolderCreated(96, 0);
        RegionTickHandle parentHandle = regions.regionizer().regionAt(0, 0).data().handle();

        regions.chunkHolderDestroyed(32, 0);
        regions.chunkHolderDestroyed(64, 0);

        parentHandle.tick();
        assertEquals(2, regionCount(), "the handle's own release is what splits");
        assertEquals(1, regions.split());
        assertTrue(parentHandle.isCancelled());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNotNull(region.data().handle());
            assertFalse(region.data().handle().isCancelled());
        }
    }

    /** Nothing positional moves any more: a merge keeps the survivor's clock, a split hands the parent's clock to every child. */
    @Test
    void mergeKeepsTheSurvivorClockAndSplitChildrenStartOnTheParentClock() {
        activateRegions();
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(96, 0);
        regions.settle();
        assertEquals(2, regionCount());
        RegionClock west = regions.regionizer().regionAt(0, 0).data().clock();
        RegionClock east = regions.regionizer().regionAt(96, 0).data().clock();
        west.advance();
        west.advance();
        east.advance();

        regions.chunkHolderCreated(32, 0);
        regions.chunkHolderCreated(64, 0);
        regions.settle();
        assertEquals(1, regionCount());
        RegionClock survivor = regions.regionizer().regionAt(0, 0).data().clock();
        assertTrue(survivor == west || survivor == east, "the survivor keeps one of the two clocks untouched");

        survivor.advance();
        regions.chunkHolderDestroyed(32, 0);
        regions.chunkHolderDestroyed(64, 0);
        regions.settle();
        assertEquals(2, regionCount());
        assertEquals(survivor.currentTick(), regions.regionizer().regionAt(0, 0).data().clock().currentTick());
        assertEquals(survivor.currentTick(), regions.regionizer().regionAt(96, 0).data().clock().currentTick());
    }

    private void activateRegions() {
        LeafsWatchdog watchdog = new LeafsWatchdog(Duration.ofSeconds(60), () -> 0L, _ -> {
        }, _ -> {
        });
        RegionTickScheduler scheduler = new RegionTickScheduler(1, false, watchdog, new RegionCrashWriter(Path.of("build", "test-crash-reports")), (_, _) -> {
        });
        regions.activate("leafs:test", scheduler, () -> 0L, time -> new RegionWorldData(time, RandomSource.create(), null, new PathTypeCache(), 0L), null);
    }

    private int regionCount() {
        return regions.regionizer().regionsView().size();
    }

    private void feedCreate(Random random, LongOpenHashSet present, LongArrayList presentList) {
        long key;
        do {
            key = CoordinateKey.pack(randomCoordinate(random), randomCoordinate(random));
        } while (!present.add(key));

        presentList.add(key);
        regions.chunkHolderCreated(CoordinateKey.x(key), CoordinateKey.z(key));
    }

    private void feedDestroy(Random random, LongOpenHashSet present, LongArrayList presentList) {
        int index = random.nextInt(presentList.size());
        long key = presentList.getLong(index);
        presentList.set(index, presentList.getLong(presentList.size() - 1));
        presentList.removeLong(presentList.size() - 1);
        present.remove(key);
        regions.chunkHolderDestroyed(CoordinateKey.x(key), CoordinateKey.z(key));
    }

    private static int randomCoordinate(Random random) {
        return (random.nextInt(POSITIONS_PER_AXIS) - POSITIONS_PER_AXIS / 2) * POSITION_STRIDE;
    }
}
