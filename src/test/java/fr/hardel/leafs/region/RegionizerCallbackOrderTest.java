package fr.hardel.leafs.region;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the exact callback orders documented on {@link RegionCallbacks}. */
class RegionizerCallbackOrderTest {
    private RecordingCallbacks callbacks;
    private Regionizer<Object> regionizer;

    @BeforeEach
    void createRegionizer() {
        callbacks = new RecordingCallbacks();
        regionizer = new Regionizer<>(4, 1, 1, callbacks);
    }

    @Test
    void creationFiresDataCreateActive() {
        regionizer.addChunk(0, 0);

        assertEquals(List.of("data #1", "create #1", "active #1"), callbacks.events);
    }

    @Test
    void mergeFiresInactiveMergeDestroyOnTheLoser() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);
        callbacks.events.clear();

        regionizer.addChunk(40, 0);

        assertEquals(List.of("inactive #2", "merge #2->#1", "destroy #2"), callbacks.events);
    }

    @Test
    void deferredMergeFiresOnlyAtRelease() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(80, 0);
        Region<Object> right = regionizer.regionAt(80, 0);
        assertTrue(right.tryMarkTicking());
        callbacks.events.clear();

        regionizer.addChunk(40, 0);
        assertEquals(List.of(), callbacks.events, "nothing may fire while the merge is deferred");

        right.markNotTicking();
        assertEquals(List.of("inactive #2", "merge #2->#1", "destroy #2"), callbacks.events, "the released region was scheduled, so the scheduler must be told to forget it");
    }

    @Test
    void splitFiresInactiveCreatesSplitDestroyActives() {
        regionizer.addChunk(0, 0);
        regionizer.addChunk(32, 0);
        regionizer.addChunk(64, 0);
        regionizer.addChunk(96, 0);
        Region<Object> parent = regionizer.regionAt(0, 0);
        regionizer.removeChunk(32, 0);
        regionizer.removeChunk(64, 0);
        assertTrue(parent.tryMarkTicking());
        callbacks.events.clear();

        parent.markNotTicking();

        assertEquals(List.of("inactive #1", "data #2", "create #2", "data #3", "create #3", "split #1->[2, 3]", "destroy #1", "active #2", "active #3"), callbacks.events);
    }

    @Test
    void emptiedRegionFiresInactiveDestroy() {
        regionizer.addChunk(0, 0);
        Region<Object> region = regionizer.regionAt(0, 0);
        regionizer.removeChunk(0, 0);
        assertTrue(region.tryMarkTicking());
        callbacks.events.clear();

        region.markNotTicking();

        assertEquals(List.of("inactive #1", "destroy #1"), callbacks.events);
    }
}
