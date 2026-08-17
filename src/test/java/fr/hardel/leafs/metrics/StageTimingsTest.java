package fr.hardel.leafs.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTimingsTest {

    @Test
    void marksAttributeElapsedTimeToEachStageInOrder() {
        StageTimings timings = new StageTimings(RegionStage.values().length);
        timings.beginTick(1_000);
        timings.mark(RegionStage.TASKS, 3_000);
        timings.mark(RegionStage.PACKETS, 6_000);
        timings.endTick();

        long[] averages = timings.averageNanos(10);

        assertEquals(2_000, averages[RegionStage.TASKS.ordinal()]);
        assertEquals(3_000, averages[RegionStage.PACKETS.ordinal()]);
        assertEquals(0, averages[RegionStage.ENTITIES.ordinal()]);
    }

    @Test
    void aStageMarkedTwiceAccumulates() {
        StageTimings timings = new StageTimings(SerialStage.values().length);
        timings.beginTick(0);
        timings.mark(SerialStage.MANAGEMENT, 100);
        timings.mark(SerialStage.TASKS, 300);
        timings.mark(SerialStage.MANAGEMENT, 600);
        timings.endTick();

        long[] averages = timings.averageNanos(1);

        assertEquals(400, averages[SerialStage.MANAGEMENT.ordinal()]);
        assertEquals(200, averages[SerialStage.TASKS.ordinal()]);
    }

    @Test
    void averagesSpanOnlyTheRequestedWindow() {
        StageTimings timings = new StageTimings(GlobalStage.values().length);
        for (int tick = 1; tick <= 3; tick++) {
            timings.beginTick(0);
            timings.mark(GlobalStage.WINDOW, tick * 1_000L);
            timings.endTick();
        }

        assertEquals(3_000, timings.averageNanos(1)[GlobalStage.WINDOW.ordinal()]);
        assertEquals(2_000, timings.averageNanos(3)[GlobalStage.WINDOW.ordinal()]);
    }

    @Test
    void marksOutsideATickAreIgnoredAndAnUnpublishedRowStaysInvisible() {
        StageTimings timings = new StageTimings(RegionStage.values().length);
        timings.mark(RegionStage.TASKS, 5_000);

        assertArrayEquals(new long[RegionStage.values().length], timings.averageNanos(10));

        timings.beginTick(0);
        timings.mark(RegionStage.TASKS, 5_000);

        assertArrayEquals(new long[RegionStage.values().length], timings.averageNanos(10), "a row is readable only after endTick");
    }

    @Test
    void theRingRecyclesPastCapacity() {
        StageTimings timings = new StageTimings(1);
        for (int tick = 0; tick < StageTimings.CAPACITY + 50; tick++) {
            timings.beginTick(0);
            timings.mark(RegionStage.TASKS, 7);
            timings.endTick();
        }

        assertEquals(7, timings.averageNanos(StageTimings.CAPACITY)[0]);
    }
}
