package fr.hardel.leafs.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedChunkHoldsTest {
    private CountingController tickets;
    private SharedChunkHolds holds;

    @BeforeEach
    void createHolds() {
        tickets = new CountingController();
        holds = new SharedChunkHolds(tickets);
    }

    @Test
    void oneTicketPerChunkNoMatterHowManyUsers() {
        holds.acquire(3, 7);
        holds.acquire(3, 7);
        holds.acquire(3, 7);

        assertEquals(1, tickets.addCalls, "vanilla keeps one ticket per (type, level) - so must we");
        assertEquals(1, holds.heldChunks());
    }

    @Test
    void lastReleaseRemovesTheTicket() {
        holds.acquire(3, 7);
        holds.acquire(3, 7);
        holds.release(3, 7);

        assertEquals(0, tickets.removeCalls);

        holds.release(3, 7);

        assertEquals(1, tickets.removeCalls);
        assertEquals(0, holds.heldChunks());
    }

    @Test
    void releaseBelowZeroThrows() {
        assertThrows(IllegalStateException.class, () -> holds.release(3, 7));
    }

    private static final class CountingController implements ChunkHoldController {
        private int addCalls;
        private int removeCalls;

        @Override
        public void addHold(int chunkX, int chunkZ) {
            addCalls++;
        }

        @Override
        public void removeHold(int chunkX, int chunkZ) {
            removeCalls++;
        }
    }
}
