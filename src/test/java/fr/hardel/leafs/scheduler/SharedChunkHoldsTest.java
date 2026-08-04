package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.Regionizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedChunkHoldsTest {
    private FakeChunkHolds tickets;
    private SharedChunkHolds holds;
    private boolean levelSerial;

    @BeforeEach
    void createHolds() {
        tickets = new FakeChunkHolds(new Regionizer<>(4, 1, 1, new TestRegionCallbacks(4)));
        levelSerial = true;
        holds = new SharedChunkHolds(tickets, () -> levelSerial);
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
    void aSecondUserReleasingDoesNotDropTheFirstUsersHold() {
        holds.acquire(3, 7);
        holds.acquire(3, 7);

        holds.release(3, 7);

        assertEquals(0, tickets.removeCalls, "the remaining user still needs the chunk");
        assertTrue(tickets.hasActiveHolds());

        holds.release(3, 7);

        assertEquals(1, tickets.removeCalls);
        assertFalse(tickets.hasActiveHolds());
        assertEquals(0, holds.heldChunks());
    }

    @Test
    void holdsOfDifferentChunksAreIndependent() {
        holds.acquire(0, 0);
        holds.acquire(100, 100);

        holds.release(0, 0);

        assertEquals(2, tickets.addCalls);
        assertEquals(1, tickets.removeCalls);
        assertEquals(1, holds.heldChunks());
    }

    @Test
    void releasingMoreThanAcquiredIsARefcountBug() {
        holds.acquire(1, 1);
        holds.release(1, 1);

        assertThrows(IllegalStateException.class, () -> holds.release(1, 1));
    }

    @Test
    void aForeignThreadDefersTicketOpsToTheQuiesce() {
        levelSerial = false;
        holds.acquire(3, 7);

        assertEquals(0, tickets.addCalls, "the raw ticket op may only run level-serial");
        assertEquals(1, holds.heldChunks());
        assertEquals(1, holds.pendingOpCount());

        levelSerial = true;
        holds.applyPendingOps();

        assertEquals(1, tickets.addCalls);
        assertEquals(0, holds.pendingOpCount());
    }

    @Test
    void anInlineCallerDrainsTheBacklogInTransitionOrder() {
        levelSerial = false;
        holds.acquire(3, 7);

        levelSerial = true;
        holds.release(3, 7);

        assertEquals(1, tickets.addCalls, "the deferred add must run before the inline remove");
        assertEquals(1, tickets.removeCalls);
        assertFalse(tickets.hasActiveHolds());
        assertEquals(0, holds.pendingOpCount());
    }

    @Test
    void applyingOpsOffTheSerialSideIsRejected() {
        levelSerial = false;
        holds.acquire(3, 7);

        assertThrows(IllegalStateException.class, holds::applyPendingOps);
    }
}
