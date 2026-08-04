package fr.hardel.leafs.ticking;

import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.RegionizerAssertions;
import fr.hardel.leafs.scheduler.ChunkHoldController;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import fr.hardel.leafs.world.RegionClock;
import fr.hardel.leafs.world.RegionWorldData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link LevelRegions} the way the chunk-holder feed does: strictly alternating create/destroy
 * per position, with one {@link LevelRegions#settle()} per simulated level tick.
 */
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
        assertEquals(0, regions.trackedChunks());
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

        regions.ownership().enterLevelSerial();
        parentHandle.tick(1);
        regions.ownership().exitLevelSerial();
        assertEquals(1, regionCount(), "a handle must skip while the level-serial side is held");
        assertEquals(0, regions.split());

        parentHandle.tick(1);
        assertEquals(2, regionCount(), "the handle's own release is what splits");
        assertEquals(1, regions.split());
        assertTrue(parentHandle.isCancelled());
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            assertNotNull(region.data().handle());
            assertFalse(region.data().handle().isCancelled());
        }
    }

    @Test
    void mergeAndSplitFoldTheWorldPayloadThroughTheRegionizer() {
        activateRegions();
        regions.chunkHolderCreated(0, 0);
        regions.chunkHolderCreated(96, 0);
        regions.settle();
        assertEquals(2, regionCount());
        RegionWorldData west = regions.regionizer().regionAt(0, 0).data().worldData();
        west.nextSubTick();
        west.nextSubTick();
        west.nextSubTick();

        regions.chunkHolderCreated(32, 0);
        regions.chunkHolderCreated(64, 0);
        regions.settle();
        assertEquals(1, regionCount());
        assertEquals(3, regions.regionizer().regionAt(0, 0).data().worldData().nextSubTick(), "the merge folds the sub-tick counter into the survivor");

        regions.chunkHolderDestroyed(32, 0);
        regions.chunkHolderDestroyed(64, 0);
        regions.settle();
        assertEquals(2, regionCount());
        assertEquals(4, regions.regionizer().regionAt(0, 0).data().worldData().nextSubTick(), "split children inherit the parent's counters");
        assertEquals(4, regions.regionizer().regionAt(96, 0).data().worldData().nextSubTick(), "split children inherit the parent's counters");
    }

    private void activateRegions() {
        LeafsWatchdog watchdog = new LeafsWatchdog(Duration.ofSeconds(60), _ -> {
        });
        RegionTickScheduler scheduler = new RegionTickScheduler(1, false, new TickBarrier(), watchdog, new RegionCrashWriter(Path.of("build", "test-crash-reports")), (_, _) -> {
        });
        SharedChunkHolds holds = new SharedChunkHolds(new ChunkHoldController() {
            @Override
            public void addHold(int chunkX, int chunkZ) {
            }

            @Override
            public void removeHold(int chunkX, int chunkZ) {
            }
        }, regions.ownership()::isLevelSerialHeldByCurrentThread);
        regions.activate("leafs:test", scheduler, holds, new RegionScheduler<>(regions.regionizer(), holds),
            () -> new RegionWorldData(new RegionClock(0L), _ -> true, new ObjectLinkedOpenHashSet<>(), RandomSource.create(), null, new HashSet<>(), new PathTypeCache()), null, () -> {
            });
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
