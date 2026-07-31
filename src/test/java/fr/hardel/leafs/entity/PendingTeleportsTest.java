package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.ChunkHoldController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingTeleportsTest {
    private final Map<Long, Integer> holdCounts = new HashMap<>();
    private final List<Runnable> submitted = new ArrayList<>();
    private final List<String> placed = new ArrayList<>();

    private final ChunkHoldController holds = new ChunkHoldController() {
        @Override
        public void acquire(int chunkX, int chunkZ) {
            holdCounts.merge(key(chunkX, chunkZ), 1, Integer::sum);
        }

        @Override
        public void release(int chunkX, int chunkZ) {
            holdCounts.merge(key(chunkX, chunkZ), -1, Integer::sum);
        }

        private long key(int chunkX, int chunkZ) {
            return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
        }
    };

    private final PendingTeleports<String> teleports = new PendingTeleports<>(holds, (_, _, placement) -> submitted.add(placement));

    private boolean allHoldsReleased() {
        return holdCounts.values().stream().allMatch(count -> count == 0);
    }

    @Test
    void scheduledPlacementRunsOnceAndReleasesHolds() {
        teleports.begin(0, 0, 100, 50, "steve", placed::add);

        assertEquals(1, teleports.pendingCount());
        assertEquals(List.of(), placed, "placement must wait for the destination's tick");

        submitted.getFirst().run();

        assertEquals(List.of("steve"), placed);
        assertEquals(0, teleports.pendingCount());
        assertTrue(allHoldsReleased());
    }

    @Test
    void shutdownCompletesEveryPendingTeleport() {
        teleports.begin(0, 0, 100, 50, "steve", placed::add);
        teleports.begin(3, 3, -40, 8, "alex", placed::add);

        teleports.completeAll();

        assertEquals(List.of("steve", "alex"), placed, "no entity may be lost at shutdown");
        assertEquals(0, teleports.pendingCount());
        assertTrue(allHoldsReleased());
    }

    @Test
    void shutdownRacingTheScheduledTaskPlacesExactlyOnce() {
        teleports.begin(0, 0, 100, 50, "steve", placed::add);

        teleports.completeAll();
        submitted.getFirst().run();

        assertEquals(List.of("steve"), placed);
        assertTrue(allHoldsReleased());
    }

    @Test
    void failedPlacementStillReleasesHolds() {
        teleports.begin(0, 0, 100, 50, "steve", _ -> {
            throw new IllegalStateException("destination refused");
        });

        assertThrows(IllegalStateException.class, () -> submitted.getFirst().run());

        assertEquals(0, teleports.pendingCount());
        assertTrue(allHoldsReleased());
    }
}
