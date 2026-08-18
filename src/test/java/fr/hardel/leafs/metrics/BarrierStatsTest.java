package fr.hardel.leafs.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarrierStatsTest {
    private static final long SECOND = 1_000_000_000L;

    private final BarrierStats stats = new BarrierStats();

    @Test
    void sampleAggregatesTheOpeningsOfTheLastMinute() {
        stats.recordOpen(10 * SECOND, 2_000_000, 3);
        stats.recordOpen(20 * SECOND, 4_000_000, 8);
        stats.recordOpen(30 * SECOND, 6_000_000, 1);

        BarrierStats.Sample sample = stats.sample(40 * SECOND);

        assertEquals(3, sample.opensPerMinute());
        assertEquals(4.0, sample.avgMs(), 0.001);
        assertEquals(2.0, sample.minMs(), 0.001);
        assertEquals(6.0, sample.maxMs(), 0.001);
        assertEquals(8, sample.deepestQueue());
    }

    @Test
    void openingsOlderThanAMinuteLeaveTheSample() {
        stats.recordOpen(10 * SECOND, 2_000_000, 3);

        assertEquals(0, stats.sample(80 * SECOND).opensPerMinute());
    }
}
