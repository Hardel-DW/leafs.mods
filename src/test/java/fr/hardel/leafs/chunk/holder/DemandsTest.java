package fr.hardel.leafs.chunk.holder;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla keeps one ticket per type and level: two waiters share it, so it leaves with the last of them, not the first. */
@ExtendWith(MinecraftBootstrap.class)
class DemandsTest {
    private static final long CHUNK = ChunkPos.pack(3, 3);
    private static final int FULL = ChunkLevel.byStatus(ChunkStatus.FULL);

    private final TicketStorage storage = new TicketStorage();
    private final Demands demands = new Demands(storage, TicketType.PLAYER_LOADING);

    @Test
    void theTicketLeavesWithTheLastWaiter() {
        demands.demand(CHUNK, FULL);
        demands.demand(CHUNK, FULL);

        demands.release(CHUNK, FULL);
        assertEquals(FULL, storage.getTicketLevelAt(CHUNK, false));
        assertTrue(demands.near(3, 3));

        demands.release(CHUNK, FULL);
        assertTrue(storage.getTickets(CHUNK).isEmpty());
        assertFalse(demands.near(3, 3));
    }

    @Test
    void aDemandAtAnotherLevelIsAnotherTicket() {
        demands.demand(CHUNK, FULL);
        demands.demand(CHUNK, FULL + 1);

        demands.release(CHUNK, FULL);

        assertEquals(FULL + 1, storage.getTicketLevelAt(CHUNK, false));
        assertTrue(demands.near(3 + ChunkLevel.RADIUS_AROUND_FULL_CHUNK, 3));
        assertFalse(demands.near(4 + ChunkLevel.RADIUS_AROUND_FULL_CHUNK, 3));
    }
}
