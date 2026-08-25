package fr.hardel.leafs.metrics;

import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTimingsTest {
    private static final TickStage FIRST = stage(0, "first");
    private static final TickStage SECOND = stage(1, "second");
    private static final TickStage THIRD = stage(2, "third");

    private static TickStage stage(int index, String name) {
        return new TickStage(TickFamily.REGION, index, Identifier.fromNamespaceAndPath("leafs", "region/" + name));
    }

    @Test
    void marksAttributeElapsedTimeToEachStageInOrder() {
        StageTimings timings = new StageTimings(3);
        timings.beginTick(1_000);
        timings.mark(FIRST, 3_000);
        timings.mark(SECOND, 6_000);
        timings.endTick(6_000);

        long[] averages = timings.averageNanos(10);

        assertEquals(2_000, averages[FIRST.index()]);
        assertEquals(3_000, averages[SECOND.index()]);
        assertEquals(0, averages[THIRD.index()]);
    }

    @Test
    void aStageMarkedTwiceAccumulates() {
        StageTimings timings = new StageTimings(3);
        timings.beginTick(0);
        timings.mark(SECOND, 100);
        timings.mark(FIRST, 300);
        timings.mark(SECOND, 600);
        timings.endTick(600);

        long[] averages = timings.averageNanos(1);

        assertEquals(400, averages[SECOND.index()]);
        assertEquals(200, averages[FIRST.index()]);
    }

    @Test
    void averagesSpanOnlyTheRequestedWindow() {
        StageTimings timings = new StageTimings(3);
        for (int tick = 1; tick <= 3; tick++) {
            timings.beginTick(0);
            timings.mark(FIRST, tick * 1_000L);
            timings.endTick(tick * 1_000L);
        }

        assertEquals(3_000, timings.averageNanos(1)[FIRST.index()]);
        assertEquals(2_000, timings.averageNanos(3)[FIRST.index()]);
    }

    @Test
    void marksOutsideATickAreIgnoredAndAnUnpublishedRowStaysInvisible() {
        StageTimings timings = new StageTimings(3);
        timings.mark(FIRST, 5_000);

        assertArrayEquals(new long[3], timings.averageNanos(10));

        timings.beginTick(0);
        timings.mark(FIRST, 5_000);

        assertArrayEquals(new long[3], timings.averageNanos(10), "a row is readable only after endTick");
    }

    @Test
    void sampleCountsOnlyCompletedTicksInTheWindow() {
        StageTimings timings = new StageTimings(1);
        long second = 1_000_000_000L;
        for (int tick = 1; tick <= 10; tick++) {
            timings.beginTick(tick * second);
            timings.endTick(tick * second + 5_000_000L);
        }

        timings.beginTick(11 * second);

        StageTimings.Snapshot snapshot = timings.sample(11 * second);
        assertEquals(5.0, snapshot.msptAverage());
        assertEquals(1.0, snapshot.tps(), 0.01, "five completed ticks over the last five seconds, the open tick counts for nothing");
    }

    @Test
    void theRingRecyclesPastCapacity() {
        StageTimings timings = new StageTimings(1);
        for (int tick = 0; tick < StageTimings.CAPACITY + 50; tick++) {
            timings.beginTick(0);
            timings.mark(FIRST, 7);
            timings.endTick(7);
        }

        assertEquals(7, timings.averageNanos(StageTimings.CAPACITY)[0]);
    }
}
