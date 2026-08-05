package fr.hardel.leafs.ownership;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The 2026-08-04 villager bed search and the 2026-08-05 disconnect-churn spawn crash: a read past
 * the loaded buffer refuses itself, and that one unit skips its tick instead of killing the region.
 */
class TickGuardTest {

    @Test
    void ownershipViolationSkipsTheUnitInsteadOfCrashingTheRegion() {
        assertDoesNotThrow(() -> TickGuard.tickOrSkip(target -> {
            throw new OwnershipViolationException("Chunk [4, 17] not present at minecraft:structure_starts in the visible map: a region worker cannot sync-load it");
        }, "chunk [5, 18] spawn pass"));
    }

    @Test
    void anyOtherFailureStillReachesTheRegionCrashPath() {
        assertThrows(IllegalStateException.class, () -> TickGuard.tickOrSkip(target -> {
            throw new IllegalStateException("genuine bug");
        }, "Villager #114"));
    }

    @Test
    void aHealthyTickRunsExactlyOnce() {
        List<String> ticked = new ArrayList<>();
        TickGuard.tickOrSkip(ticked::add, "Villager #114");

        assertEquals(List.of("Villager #114"), ticked);
    }

    /** A structure search refused mid-validation degrades onto vanilla's own not-found result. */
    @Test
    void aRefusedLookupDegradesOntoNotFound() {
        assertEquals("stronghold", TickGuard.callOrNull(() -> "stronghold", "eye_of_ender"));
        assertNull(TickGuard.callOrNull(() -> {
            throw new OwnershipViolationException("Chunk [4, 17] not present");
        }, "eye_of_ender"));
    }
}
