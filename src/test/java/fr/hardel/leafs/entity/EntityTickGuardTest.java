package fr.hardel.leafs.entity;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The overstress crash of 2026-08-04 (region #21): a villager's {@code AcquirePoi} validated a bed
 * several chunks away, the read refused itself with an {@code OwnershipViolationException} on a
 * chunk not yet FULL, and the whole region died for one transient streaming gap. Compromise #19:
 * that one entity skips its tick; every other failure keeps the vanilla crash path.
 */
class EntityTickGuardTest {

    @Test
    void ownershipViolationSkipsTheEntityInsteadOfCrashingTheRegion() {
        assertDoesNotThrow(() -> EntityTickGuard.tickOrSkip(entity -> {
            throw new OwnershipViolationException("Chunk [3785, 5758] not present at minecraft:full in the visible map: a region worker cannot sync-load it");
        }, "Villager #114"));
    }

    @Test
    void anyOtherFailureStillReachesTheRegionCrashPath() {
        assertThrows(IllegalStateException.class, () -> EntityTickGuard.tickOrSkip(entity -> {
            throw new IllegalStateException("genuine entity bug");
        }, "Villager #114"));
    }

    @Test
    void aHealthyTickRunsExactlyOnce() {
        List<String> ticked = new ArrayList<>();
        EntityTickGuard.tickOrSkip(ticked::add, "Villager #114");

        assertEquals(List.of("Villager #114"), ticked);
    }
}
