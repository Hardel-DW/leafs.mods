package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.ChunkHoldController;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingTeleportsTest {
    private final List<String> originTickets = new ArrayList<>();
    private final List<String> destinationTickets = new ArrayList<>();
    private final List<Runnable> submitted = new ArrayList<>();
    private final List<String> placed = new ArrayList<>();

    private final SharedChunkHolds originHolds = new SharedChunkHolds(new CountingHolds(originTickets), () -> true);
    private final SharedChunkHolds destinationHolds = new SharedChunkHolds(new CountingHolds(destinationTickets), () -> true);

    /** Stands in for the destination's RegionScheduler: a queued region task holds its target until it runs. */
    private final PendingTeleports<String> teleports = new PendingTeleports<>((chunkX, chunkZ, placement) -> {
        destinationHolds.acquire(chunkX, chunkZ);
        submitted.add(() -> {
            try {
                placement.run();
            } finally {
                destinationHolds.release(chunkX, chunkZ);
            }
        });
    });

    private record CountingHolds(List<String> tickets) implements ChunkHoldController {
        @Override
        public void addHold(int chunkX, int chunkZ) {
            tickets.add(chunkX + "," + chunkZ);
        }

        @Override
        public void removeHold(int chunkX, int chunkZ) {
            tickets.remove(chunkX + "," + chunkZ);
        }
    }

    @Test
    void scheduledPlacementRunsOnceAndReleasesHolds() {
        teleports.begin(originHolds, 0, 0, 100, 50, "steve", placed::add);

        assertEquals(1, teleports.pendingCount());
        assertEquals(List.of(), placed, "placement must wait for the destination's tick");
        assertEquals(List.of("0,0"), originTickets, "the origin is held on its own level's table");
        assertEquals(List.of("100,50"), destinationTickets);

        submitted.getFirst().run();

        assertEquals(List.of("steve"), placed);
        assertEquals(0, teleports.pendingCount());
        assertEquals(List.of(), originTickets);
        assertEquals(List.of(), destinationTickets);
    }

    @Test
    void anOriginEqualToTheDestinationStillHoldsBothSides() {
        teleports.begin(originHolds, 4, 4, 4, 4, "steve", placed::add);

        assertEquals(List.of("4,4"), originTickets);
        assertEquals(List.of("4,4"), destinationTickets);

        submitted.getFirst().run();

        assertEquals(List.of("steve"), placed);
        assertEquals(List.of(), originTickets);
        assertEquals(List.of(), destinationTickets);
    }

    @Test
    void shutdownCompletesEveryPendingTeleport() {
        teleports.begin(originHolds, 0, 0, 100, 50, "steve", placed::add);
        teleports.begin(originHolds, 3, 3, -40, 8, "alex", placed::add);

        teleports.completeAll();

        assertEquals(List.of("steve", "alex"), placed, "no entity may be lost at shutdown");
        assertEquals(0, teleports.pendingCount());
        assertEquals(List.of(), originTickets);
    }

    @Test
    void shutdownRacingTheScheduledTaskPlacesExactlyOnce() {
        teleports.begin(originHolds, 0, 0, 100, 50, "steve", placed::add);

        teleports.completeAll();
        submitted.getFirst().run();

        assertEquals(List.of("steve"), placed);
        assertEquals(List.of(), originTickets);
        assertEquals(List.of(), destinationTickets);
    }

    @Test
    void failedPlacementStillReleasesHolds() {
        teleports.begin(originHolds, 0, 0, 100, 50, "steve", _ -> {
            throw new IllegalStateException("destination refused");
        });

        assertThrows(IllegalStateException.class, () -> submitted.getFirst().run());

        assertEquals(0, teleports.pendingCount());
        assertEquals(List.of(), originTickets);
        assertEquals(List.of(), destinationTickets);
    }
}
