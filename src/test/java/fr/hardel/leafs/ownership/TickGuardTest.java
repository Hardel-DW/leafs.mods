package fr.hardel.leafs.ownership;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 2026-08-04 and 2026-08-05 crashes: a read past the buffer refuses, that one unit skips its tick. */
class TickGuardTest {

    @Test
    void ownershipViolationSkipsTheUnitInsteadOfCrashingTheRegion() {
        assertDoesNotThrow(() -> TickGuard.tickOrSkip(target -> {
            throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "Chunk [4, 17] not present at minecraft:structure_starts in the visible map");
        }, "chunk [5, 18] spawn pass"));
    }

    @Test
    void aGuardedDrainSkipsTheRefusedItemAndKeepsGoing() {
        List<String> ticked = new ArrayList<>();
        List<String> requeued = new ArrayList<>();
        BiConsumer<String, String> guarded = TickGuard.guardingWithRetry((first, second) -> {
            if (first.equals("refused")) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN, "another region owns it");
            }

            ticked.add(first);
        }, (first, second) -> requeued.add(first));

        guarded.accept("one", "");
        assertDoesNotThrow(() -> guarded.accept("refused", ""));
        guarded.accept("two", "");

        assertEquals(List.of("one", "two"), ticked);
        assertEquals(List.of("refused"), requeued);
    }

    @Test
    void aRefusedScheduledTickRequeuesExactlyOnce() {
        List<String> requeued = new ArrayList<>();
        BiConsumer<String, String> guarded = TickGuard.guardingWithRetry((first, second) -> {
            throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "chunk not present");
        }, (first, second) -> requeued.add(first));

        guarded.accept("fluid at the border", "");

        assertEquals(List.of("fluid at the border"), requeued);
    }

    @Test
    void aGenuineFailureInAGuardedDrainStillCrashes() {
        BiConsumer<String, String> guarded = TickGuard.guardingWithRetry((first, second) -> {
            throw new IllegalStateException("genuine bug");
        }, (first, second) -> {
        });

        assertThrows(IllegalStateException.class, () -> guarded.accept("hopper", ""));
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
}
