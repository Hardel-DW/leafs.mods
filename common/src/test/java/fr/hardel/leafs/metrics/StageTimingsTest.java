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
    private static final long PERIOD = 50_000_000L;

    private static TickStage stage(int index, String name) {
        return new TickStage(TickFamily.REGION, index, Identifier.fromNamespaceAndPath("leafs", "region/" + name));
    }

    @Test
    void marksAttributeElapsedTimeToEachStageInOrder() {
        StageTimings timings = new StageTimings(3, () -> PERIOD);
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
        StageTimings timings = new StageTimings(3, () -> PERIOD);
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
        StageTimings timings = new StageTimings(3, () -> PERIOD);
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
        StageTimings timings = new StageTimings(3, () -> PERIOD);
        timings.mark(FIRST, 5_000);

        assertArrayEquals(new long[3], timings.averageNanos(10));

        timings.beginTick(0);
        timings.mark(FIRST, 5_000);

        assertArrayEquals(new long[3], timings.averageNanos(10), "a row is readable only after endTick");
    }

    @Test
    void sampleCountsOnlyCompletedTicksInTheWindow() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);
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
    void aUnitThatNeverTickedRunsAtTheTargetRate() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);

        assertEquals(20.0, timings.sample(7 * PERIOD).tps());
    }

    @Test
    void aFirstTickInProgressForLessThanAPeriodKeepsTheTargetRate() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);
        timings.beginTick(0);

        assertEquals(20.0, timings.sample(PERIOD / 2).tps());
    }

    @Test
    void aFirstTickLongerThanAPeriodLowersTheRate() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);
        timings.beginTick(0);
        timings.endTick(8 * PERIOD);

        assertEquals(5.0, timings.sample(8 * PERIOD).tps(), 0.01, "the start of the first tick and its end over eight periods");
    }

    @Test
    void theTargetRateFollowsTheTickRate() {
        StageTimings timings = new StageTimings(1, () -> PERIOD / 2);
        for (int tick = 0; tick < 40; tick++) {
            timings.beginTick(tick * PERIOD / 2);
            timings.endTick(tick * PERIOD / 2 + 1_000_000L);
        }

        assertEquals(40.0, timings.sample(20 * PERIOD).tps(), 0.01);
    }

    @Test
    void theRingRecyclesPastCapacity() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);
        for (int tick = 0; tick < StageTimings.CAPACITY + 50; tick++) {
            timings.beginTick(0);
            timings.mark(FIRST, 7);
            timings.endTick(7);
        }

        assertEquals(7, timings.averageNanos(StageTimings.CAPACITY)[0]);
    }

    @Test
    void rowsSinceHandsEveryTickEndedAfterTheGivenCount() {
        StageTimings timings = new StageTimings(3, () -> PERIOD);
        for (int tick = 1; tick <= 3; tick++) {
            timings.beginTick(0);
            timings.mark(FIRST, tick);
            timings.endTick(tick);
        }

        long[][] rows = timings.rowsSince(1);

        assertEquals(2, rows.length);
        assertEquals(2, rows[0][FIRST.index()]);
        assertEquals(3, rows[1][FIRST.index()]);
        assertEquals(0, timings.rowsSince(3).length);
    }

    @Test
    void rowsSinceStopsAtWhatTheRingStillHolds() {
        StageTimings timings = new StageTimings(1, () -> PERIOD);
        for (int tick = 0; tick < StageTimings.CAPACITY + 10; tick++) {
            timings.beginTick(0);
            timings.mark(FIRST, tick);
            timings.endTick(tick);
        }

        long[][] rows = timings.rowsSince(0);

        assertEquals(StageTimings.CAPACITY - 1, rows.length);
        assertEquals(11, rows[0][0]);
    }
}
