package fr.hardel.leafs.entity;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The 2026-08-04 overstress crash: a villager's bed search read a chunk not yet FULL and the whole
 * region died for one transient streaming gap (Compromise #19).
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
