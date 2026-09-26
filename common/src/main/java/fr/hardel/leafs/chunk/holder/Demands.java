package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.pool.ChunkNeed;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.ConcurrentHashMap;

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

    public boolean needs(int chunkX, int chunkZ, ChunkStatus status) {
        for (Key key : waiters.keySet()) {
            ChunkNeed need = ChunkNeed.of(ChunkPyramid.GENERATION_PYRAMID, ChunkPos.getX(key.chunkKey()), ChunkPos.getZ(key.chunkKey()), ChunkLevel.generationStatus(key.level()));
            if (need.covers(chunkX, chunkZ, status)) {
                return true;
            }
        }

        return false;
    }
}
