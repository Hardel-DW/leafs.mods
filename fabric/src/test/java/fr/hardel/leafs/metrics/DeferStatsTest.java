package fr.hardel.leafs.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferStatsTest {

    private final DeferStats stats = new DeferStats();

    @Test
    void countersAreIndependentPerReason() {
        stats.countDeferral(DeferReason.PLAYER_TELEPORT);
        stats.countDeferral(DeferReason.PLAYER_TELEPORT);
        stats.countDrop(DeferReason.RESPAWN);

        assertEquals(2, stats.deferrals(DeferReason.PLAYER_TELEPORT).perMinute());
        assertEquals(0, stats.deferrals(DeferReason.PORTAL).perMinute());
        assertEquals(1, stats.drops(DeferReason.RESPAWN).perMinute());
        assertEquals(0, stats.drops(DeferReason.TELEPORT).perMinute());
    }
}
