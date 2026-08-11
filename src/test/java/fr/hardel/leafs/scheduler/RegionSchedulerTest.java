package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.Regionizer;
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
    private FakeChunkHolds tickets;
    private SharedChunkHolds holds;
    private RegionScheduler<TestRegionData> scheduler;
    private List<String> executed;

    @BeforeEach
    void createScheduler() {
        regionizer = new Regionizer<>(SECTION_SHIFT, 1, 1, new TestRegionCallbacks(SECTION_SHIFT));
        tickets = new FakeChunkHolds(regionizer);
        holds = new SharedChunkHolds(tickets);
        scheduler = new RegionScheduler<>(regionizer, holds);
        executed = new ArrayList<>();
    }

    @Test
    void queueToAnUnloadedPositionMaterialisesARegion() {
        scheduler.queue(0, 0, () -> executed.add("task"));

        assertEquals(1, tickets.addCalls, "the hold ticket applies inline from the queueing thread");
        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        assertNotNull(region, "the chunk hold must have created a region");
        assertEquals(List.of(), executed);

        assertEquals(1, scheduler.drain(region));

        assertEquals(List.of("task"), executed);
        assertFalse(tickets.hasActiveHolds(), "the hold must be released once the task ran");
    }

    @Test
    void drainRunsTasksInSubmissionOrderAndRefcountsHolds() {
        scheduler.queue(0, 0, () -> executed.add("first"));
        scheduler.queue(0, 0, () -> executed.add("second"));
        scheduler.queue(0, 0, () -> executed.add("third"));

        assertEquals(1, tickets.addCalls, "one ticket per chunk, refcounted across tasks");
        assertEquals(3, scheduler.drain(regionizer.regionAt(0, 0)));
        assertEquals(List.of("first", "second", "third"), executed);
        assertEquals(1, tickets.removeCalls);
    }

    @Test
    void mergeMovesQueuedTasksInFoliaOrder() {
        tickets.loadChunk(0, 0);
        tickets.loadChunk(80, 0);
        scheduler.queue(0, 0, () -> executed.add("west1"));
        scheduler.queue(0, 0, () -> executed.add("west2"));
        scheduler.queue(80, 0, () -> executed.add("east1"));

        tickets.loadChunk(40, 0);

        Region<TestRegionData> merged = regionizer.regionAt(40, 0);
        assertEquals(3, scheduler.drain(merged));
        assertEquals(List.of("east1", "west1", "west2"), executed, "the closing queue's tasks land before the target's");
    }

    @Test
    void splitReroutesTasksToTheChildOwningTheirPosition() {
        tickets.loadChunk(0, 0);
        tickets.loadChunk(32, 0);
        tickets.loadChunk(64, 0);
        tickets.loadChunk(96, 0);
        scheduler.queue(0, 0, () -> executed.add("west"));
        scheduler.queue(96, 0, () -> executed.add("east"));

        tickets.unloadChunk(32, 0);
        tickets.unloadChunk(64, 0);
        Region<TestRegionData> parent = regionizer.regionAt(0, 0);
        assertTrue(parent.tryMarkTicking());
        parent.markNotTicking();

        Region<TestRegionData> west = regionizer.regionAt(0, 0);
        Region<TestRegionData> east = regionizer.regionAt(96, 0);
        assertEquals(1, scheduler.drain(west));
        assertEquals(List.of("west"), executed);
        assertEquals(1, scheduler.drain(east));
        assertEquals(List.of("west", "east"), executed);
        assertFalse(tickets.hasActiveHolds());
    }

    @Test
    void throwingTaskLeavesTheOnesBehindItQueuedAndHeld() {
        scheduler.queue(0, 0, () -> executed.add("before"));
        scheduler.queue(0, 0, () -> {
            throw new IllegalStateException("boom");
        });
        scheduler.queue(0, 0, () -> executed.add("after"));

        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        assertThrows(IllegalStateException.class, () -> scheduler.drain(region));

        assertEquals(List.of("before"), executed);
        assertTrue(tickets.hasActiveHolds(), "the surviving task still holds its chunk");
        assertEquals(1, scheduler.drain(region), "nothing behind the throw was discarded");
        assertEquals(List.of("before", "after"), executed);
        assertFalse(tickets.hasActiveHolds());
    }

    @Test
    void aHoldThatMaterialisesNoRegionFailsLoudlyWithoutLeaking() {
        SharedChunkHolds inertHolds = inertHolds();
        RegionScheduler<TestRegionData> inert = new RegionScheduler<>(regionizer, inertHolds);
        inert.queue(0, 0, () -> executed.add("never"));

        assertThrows(IllegalStateException.class, inert::completePending);
        assertEquals(0, inertHolds.heldChunks(), "the failed completion must not keep its hold");
    }

    /** A task queued in the shutdown race must still run; the shutdown drain runs it inline. */
    @Test
    void drainPendingInlineRunsUnmaterialisedTasksAndReleasesHolds() {
        SharedChunkHolds inertHolds = inertHolds();
        RegionScheduler<TestRegionData> inert = new RegionScheduler<>(regionizer, inertHolds);
        inert.queue(0, 0, () -> executed.add("stranded"));

        assertEquals(1, inert.drainPendingInline());
        assertEquals(List.of("stranded"), executed);
        assertEquals(0, inertHolds.heldChunks());
    }

    @Test
    void tasksQueuedDuringADrainWaitForTheNext() {
        scheduler.queue(0, 0, () -> {
            executed.add("first");
            scheduler.queue(0, 0, () -> executed.add("requeued"));
        });

        Region<TestRegionData> region = regionizer.regionAt(0, 0);
        assertEquals(1, scheduler.drain(region));
        assertEquals(List.of("first"), executed);
        assertEquals(1, scheduler.drain(region));
        assertEquals(List.of("first", "requeued"), executed);
        assertFalse(tickets.hasActiveHolds());
    }

    /** A controller that never loads a chunk, so no region ever materialises for the queued task. */
    private static SharedChunkHolds inertHolds() {
        return new SharedChunkHolds(new ChunkHoldController() {
            @Override
            public void addHold(int chunkX, int chunkZ) {
            }

            @Override
            public void removeHold(int chunkX, int chunkZ) {
            }
        });
    }
}
