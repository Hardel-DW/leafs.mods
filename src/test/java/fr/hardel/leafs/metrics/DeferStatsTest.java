package fr.hardel.leafs.metrics;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferStatsTest {

    private final DeferStats stats = new DeferStats();

    @Test
    void countersAreIndependentPerReason() {
        stats.countDeferral(DeferReason.COMMAND_BLOCK);
        stats.countDeferral(DeferReason.COMMAND_BLOCK);
        stats.countRetry(DeferReason.PORTAL);
        stats.countDrop(DeferReason.RESPAWN);

        assertEquals(2, stats.deferrals(DeferReason.COMMAND_BLOCK).total());
        assertEquals(0, stats.deferrals(DeferReason.PORTAL).total());
        assertEquals(1, stats.retries(DeferReason.PORTAL).total());
        assertEquals(1, stats.drops(DeferReason.RESPAWN).total());
        assertEquals(0, stats.drops(DeferReason.TELEPORT).total());
    }

    @Test
    void refusalsSplitByKindAndSource() {
        stats.countRefusal(OwnershipViolationException.Kind.ABSENT, DeferStats.RefusalSource.REGION);
        stats.countRefusal(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.FOREIGN_THREAD);

        assertEquals(1, stats.refusals(OwnershipViolationException.Kind.ABSENT, DeferStats.RefusalSource.REGION).total());
        assertEquals(1, stats.refusals(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.FOREIGN_THREAD).total());
        assertEquals(0, stats.refusals(OwnershipViolationException.Kind.FOREIGN, DeferStats.RefusalSource.REGION).total());
    }
}
