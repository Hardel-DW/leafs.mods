package fr.hardel.leafs.chunk.holder;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.ConcurrentHashMap;

/** The waiters per chunk and level. Vanilla merges equal tickets, so the first waiter posts the ticket and the last removes it, inside the count's own atomic step. */
public final class Demands {
    private record Key(long chunkKey, int level) {}
    private final TicketStorage tickets;
    private final TicketType type;
    private final ConcurrentHashMap<Key, Integer> waiters = new ConcurrentHashMap<>();

    public Demands(TicketStorage tickets, TicketType type) {
        this.tickets = tickets;
        this.type = type;
    }

    public void demand(long chunkKey, int level) {
        waiters.compute(new Key(chunkKey, level), (_, count) -> {
            if (count == null) {
                tickets.addTicket(chunkKey, new Ticket(type, level));
                return 1;
            }

            return count + 1;
        });
    }

    public void release(long chunkKey, int level) {
        waiters.compute(new Key(chunkKey, level), (_, count) -> {
            if (count == 1) {
                tickets.removeTicket(chunkKey, new Ticket(type, level));
                return null;
            }

            return count - 1;
        });
    }

    /** Whether a demanded chunk lies within vanilla's radius of this one: what the pool serves first. */
    public boolean near(int chunkX, int chunkZ) {
        for (Key key : waiters.keySet()) {
            int distance = Math.max(Math.abs(ChunkPos.getX(key.chunkKey()) - chunkX), Math.abs(ChunkPos.getZ(key.chunkKey()) - chunkZ));
            if (distance <= ChunkLevel.RADIUS_AROUND_FULL_CHUNK) {
                return true;
            }
        }

        return false;
    }
}
