package fr.hardel.leafs.ticking;

import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.region.RegionizerAssertions;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        for (Region<Void> region : regions.regionizer().regionsView()) {
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
