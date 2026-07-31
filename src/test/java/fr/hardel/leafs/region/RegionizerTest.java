package fr.hardel.leafs.region;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scenario tests with shift 4 (16x16-chunk sections), merge radius 1, buffer radius 1. */
class RegionizerTest {
    private RecordingCallbacks callbacks;
    private Regionizer<Object> regionizer;

    @BeforeEach
    void createRegionizer() {
        callbacks = new RecordingCallbacks();
        regionizer = new Regionizer<>(4, 1, 1, callbacks);
    }

    @Test
    void singleChunkCreatesARegionWithItsBuffer() {
        regionizer.addChunk(0, 0);

        Region<Object> region = regionizer.regionAt(0, 0);
        assertNotNull(region);
        assertEquals(RegionState.READY, region.state());
        assertEquals(9, region.sectionCount());
        assertEquals(1, region.chunkCount());
        assertEquals(9, regionizer.sectionsView().size());
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void additionsInsideANonEmptySectionTakeTheFastPath() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(1, 0);
        regionizer.addChunk(15, 15);

        Region<Object> region = regionizer.regionAt(15, 15);
        assertEquals(3, region.chunkCount());
        assertEquals(9, region.sectionCount());
        assertEquals(1, regionizer.regionsView().size());
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void neighbouringSectionsShareOneRegion() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(16, 0);

        assertEquals(1, regionizer.regionsView().size());
        assertSame(regionizer.regionAt(0, 0), regionizer.regionAt(16, 0));
        assertEquals(12, regionizer.regionAt(0, 0).sectionCount());
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void distantChunksCreateSeparateRegions() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);

        assertEquals(2, regionizer.regionsView().size());
        assertNotSame(regionizer.regionAt(0, 0), regionizer.regionAt(80, 0));
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void negativeCoordinatesUseFlooredSections() {
        regionizer.addChunk(-1, -1);
        regionizer.addChunk(-17, 3);

        assertEquals(1, regionizer.regionsView().size());
        assertSame(regionizer.regionAt(-1, -1), regionizer.regionAt(-17, 3));
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void bridgingChunkMergesTwoReadyRegions() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);

        regionizer.addChunk(40, 0);

        assertEquals(1, regionizer.regionsView().size());
        Region<Object> merged = regionizer.regionAt(40, 0);
        assertSame(merged, regionizer.regionAt(0, 0));
        assertSame(merged, regionizer.regionAt(80, 0));
        assertEquals(3, merged.chunkCount());
        assertEquals(1, callbacks.events.stream().filter(event -> event.startsWith("merge ")).count());
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void mergeIntoATickingRegionIsDeferredUntilItsRelease() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);
        Region<Object> left = regionizer.regionAt(0, 0);
        Region<Object> right = regionizer.regionAt(80, 0);
        assertTrue(right.tryMarkTicking());

        regionizer.addChunk(40, 0);

        assertEquals(2, regionizer.regionsView().size());
        assertTrue(right.mergeIntoLater.contains(left));
        assertFalse(left.tryMarkTicking(), "a region expecting a merge must not tick");
        RegionizerAssertions.assertInvariants(regionizer, false);

        right.markNotTicking();

        assertEquals(1, regionizer.regionsView().size());
        assertEquals(RegionState.DEAD, right.state());
        assertSame(left, regionizer.regionAt(80, 0));
        assertTrue(left.tryMarkTicking());
        left.markNotTicking();
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void bridgingWhileEverythingTicksCreatesAFreshTarget() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);
        Region<Object> left = regionizer.regionAt(0, 0);
        Region<Object> right = regionizer.regionAt(80, 0);
        assertTrue(left.tryMarkTicking());
        assertTrue(right.tryMarkTicking());

        regionizer.addChunk(40, 0);

        assertEquals(3, regionizer.regionsView().size());
        Region<Object> target = regionizer.regionAt(40, 0);
        assertNotSame(left, target);
        assertNotSame(right, target);
        assertFalse(target.tryMarkTicking(), "the fresh target still expects merges from the ticking regions");
        RegionizerAssertions.assertInvariants(regionizer, false);

        left.markNotTicking();
        right.markNotTicking();

        assertEquals(1, regionizer.regionsView().size());
        assertSame(target, regionizer.regionAt(0, 0));
        assertSame(target, regionizer.regionAt(80, 0));
        assertTrue(target.tryMarkTicking());
        target.markNotTicking();
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void removingTheLastChunkKillsTheRegionOnRelease() {
        regionizer.addChunk(0, 0);
        Region<Object> region = regionizer.regionAt(0, 0);

        regionizer.removeChunk(0, 0);

        assertEquals(9, region.deadSectionKeys.size(), "all sections should be dead once the only chunk is gone");
        RegionizerAssertions.assertInvariants(regionizer, true);

        assertTrue(region.tryMarkTicking());
        region.markNotTicking();

        assertEquals(RegionState.DEAD, region.state());
        assertTrue(regionizer.regionsView().isEmpty());
        assertTrue(regionizer.sectionsView().isEmpty());
        assertNull(regionizer.regionAt(0, 0));
    }

    @Test
    void removingTheMiddleOfAChainSplitsTheRegionOnRelease() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(32, 0);
        regionizer.addChunk(64, 0);
        regionizer.addChunk(96, 0);
        assertEquals(1, regionizer.regionsView().size());
        Region<Object> parent = regionizer.regionAt(0, 0);

        regionizer.removeChunk(32, 0);
        regionizer.removeChunk(64, 0);
        assertTrue(parent.tryMarkTicking());
        parent.markNotTicking();

        assertEquals(RegionState.DEAD, parent.state());
        assertEquals(2, regionizer.regionsView().size());
        Region<Object> west = regionizer.regionAt(0, 0);
        Region<Object> east = regionizer.regionAt(96, 0);
        assertNotSame(west, east);
        assertEquals(1, west.chunkCount());
        assertEquals(1, east.chunkCount());
        assertEquals(1, callbacks.events.stream().filter(event -> event.startsWith("split ")).count());
        RegionizerAssertions.assertInvariants(regionizer, true);
    }

    @Test
    void doubleAddAndUnknownRemoveCrashEarly() {
        regionizer.addChunk(0, 0);

        assertThrows(IllegalStateException.class, () -> regionizer.addChunk(0, 0));
        assertThrows(IllegalStateException.class, () -> regionizer.removeChunk(1, 1));
        assertThrows(IllegalStateException.class, () -> regionizer.removeChunk(500, 500));
    }

    @Test
    void callbacksMustNotReenterTheRegionizer() {
        AtomicReference<Regionizer<Object>> holder = new AtomicReference<>();
        Regionizer<Object> reentrant = new Regionizer<>(4, 1, 1, new RecordingCallbacks() {
            @Override
            public void onRegionCreate(Region<Object> region) {
                holder.get().addChunk(500, 500);
            }
        });
        holder.set(reentrant);

        assertThrows(IllegalStateException.class, () -> reentrant.addChunk(0, 0));
    }
}
