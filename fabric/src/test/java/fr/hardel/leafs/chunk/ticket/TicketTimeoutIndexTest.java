package fr.hardel.leafs.chunk.ticket;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class TicketTimeoutIndexTest {
    private static final long CHUNK = ChunkPos.pack(3, 3);
    private static final long SECTION = ChunkPos.pack(1, 1);

    @Test
    void aTicketExpiresOnceItsTimeoutIsCountedDown() {
        TicketStorage storage = new TicketStorage();
        TicketStorageAccess access = (TicketStorageAccess) storage;
        TicketTimeoutIndex timeouts = new TicketTimeoutIndex(storage, null, access.leafs$graphs(), 1);
        access.leafs$bindTimeouts(timeouts);
        storage.addTicket(CHUNK, new Ticket(TicketType.UNKNOWN, 33));

        for (long countdown = 0; countdown <= TicketType.UNKNOWN.timeout(); countdown++) {
            assertEquals(1, storage.getTickets(CHUNK).size());
            timeouts.purgeSections(new long[]{SECTION});
        }

        assertTrue(storage.getTickets(CHUNK).isEmpty());
    }
}
