package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSchedulerTest {
    private static final int SECTION_SHIFT = 4;

    private Regionizer<TestRegionData> regionizer;
    private FakeChunkHolds holds;
    private RegionScheduler<TestRegionData> scheduler;
    private List<String> executed;

    @BeforeEach
    void createScheduler() {
        regionizer = new Regionizer<>(SECTION_SHIFT, 1, 1, new TestRegionCallbacks(SECTION_SHIFT));
        holds = new FakeChunkHolds(regionizer);
        scheduler = new RegionScheduler<>(regionizer, holds);
        executed = new ArrayList<>();
    }

    @AfterEach
    void clearContext() {
        RegionContext.exit();
    }

    @Test
    void queueToAnUnloadedPositionMaterialisesARegion() {
        scheduler.queue(0, 0, () -> executed.add("task"));

        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        assertNotNull(region, "the chunk hold must have created a region");
        assertEquals(List.of(), executed);

        assertEquals(1, scheduler.drain(region));

        assertEquals(List.of("task"), executed);
        assertFalse(holds.hasActiveHolds(), "the hold must be released once the task ran");
    }

    @Test
    void drainRunsTasksInSubmissionOrderAndRefcountsHolds() {
        scheduler.queue(0, 0, () -> executed.add("first"));
        scheduler.queue(0, 0, () -> executed.add("second"));
        scheduler.queue(0, 0, () -> executed.add("third"));

        assertEquals(1, holds.acquireCalls, "one hold per chunk, refcounted across tasks");
        assertEquals(3, scheduler.drain(regionizer.regionAt(0, 0)));
        assertEquals(List.of("first", "second", "third"), executed);
        assertEquals(1, holds.releaseCalls);
    }

    @Test
    void runExecutesInlineOnTheOwningRegionThread() {
        holds.loadChunk(0, 0);
        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        RegionContext.enter(new RegionContext.Region(region.id(), "test:world"));

        scheduler.run(0, 0, () -> executed.add("inline"));

        assertEquals(List.of("inline"), executed);
        assertEquals(0, holds.acquireCalls);
        assertEquals(0, scheduler.drain(region), "nothing may have been queued");
    }

    @Test
    void runFromAForeignContextQueues() {
        holds.loadChunk(0, 0);
        holds.loadChunk(80, 0);
        Region<TestRegionData> other = regionizer.regionAt(80, 0);
        RegionContext.enter(new RegionContext.Region(other.id(), "test:world"));

        scheduler.run(0, 0, () -> executed.add("task"));

        assertEquals(List.of(), executed);
        assertEquals(1, scheduler.drain(regionizer.regionAt(0, 0)));
        assertEquals(List.of("task"), executed);
    }

    @Test
    void mergeMovesQueuedTasksInFoliaOrder() {
        holds.loadChunk(0, 0);
        holds.loadChunk(80, 0);
        scheduler.queue(0, 0, () -> executed.add("west1"));
        scheduler.queue(0, 0, () -> executed.add("west2"));
        scheduler.queue(80, 0, () -> executed.add("east1"));

        holds.loadChunk(40, 0);

        Region<TestRegionData> merged = regionizer.regionAt(40, 0);
        assertEquals(3, scheduler.drain(merged));
        assertEquals(List.of("east1", "west1", "west2"), executed, "the closing queue's tasks land before the target's");
    }

    @Test
    void splitReroutesTasksToTheChildOwningTheirPosition() {
        holds.loadChunk(0, 0);
        holds.loadChunk(32, 0);
        holds.loadChunk(64, 0);
        holds.loadChunk(96, 0);
        scheduler.queue(0, 0, () -> executed.add("west"));
        scheduler.queue(96, 0, () -> executed.add("east"));

        holds.unloadChunk(32, 0);
        holds.unloadChunk(64, 0);
        Region<TestRegionData> parent = regionizer.regionAt(0, 0);
        assertTrue(parent.tryMarkTicking());
        parent.markNotTicking();

        Region<TestRegionData> west = regionizer.regionAt(0, 0);
        Region<TestRegionData> east = regionizer.regionAt(96, 0);
        assertEquals(1, scheduler.drain(west));
        assertEquals(List.of("west"), executed);
        assertEquals(1, scheduler.drain(east));
        assertEquals(List.of("west", "east"), executed);
        assertFalse(holds.hasActiveHolds());
    }

    @Test
    void throwingTaskPropagatesAndStillReleasesItsHold() {
        scheduler.queue(0, 0, () -> {
            throw new IllegalStateException("boom");
        });

        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        assertThrows(IllegalStateException.class, () -> scheduler.drain(region));
        assertFalse(holds.hasActiveHolds());
    }
}
