package fr.hardel.leafs.metrics;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferStatsTest {

    private final DeferStats stats = new DeferStats();

    @Test
    void countersAreIndependentPerReason() {
        stats.countDeferral(DeferReason.PLAYER_PLACEMENT);
        stats.countDeferral(DeferReason.PLAYER_PLACEMENT);
        stats.countRetry(DeferReason.PORTAL);
        stats.countDrop(DeferReason.RESPAWN);

        assertEquals(2, stats.deferrals(DeferReason.PLAYER_PLACEMENT).perMinute());
        assertEquals(0, stats.deferrals(DeferReason.PORTAL).perMinute());
        assertEquals(1, stats.retries(DeferReason.PORTAL).perMinute());
        assertEquals(1, stats.drops(DeferReason.RESPAWN).perMinute());
        assertEquals(0, stats.drops(DeferReason.TELEPORT).perMinute());
    }

    @Test
    void refusalsSplitByKindAndSource() {
        stats.countRefusal(OwnershipViolationException.Kind.ABSENT, DeferStats.RefusalSource.REGION);
        stats.countRefusal(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.FOREIGN_THREAD);

        assertEquals(1, stats.refusals(OwnershipViolationException.Kind.ABSENT, DeferStats.RefusalSource.REGION).perMinute());
        assertEquals(1, stats.refusals(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.FOREIGN_THREAD).perMinute());
        assertEquals(0, stats.refusals(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.REGION).perMinute());
    }
}
