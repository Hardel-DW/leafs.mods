package fr.hardel.leafs.chunk;

import fr.hardel.leafs.scheduler.ChunkHoldController;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/**
 * The real {@link ChunkHoldController}: one HOLD ticket at holder-alive level per held chunk. The
 * scheduler refcounts, so acquire/release pair exactly one ticket. Must be called on a thread that
 * owns the level's ticket storage (the regionised ticket entry point arrives with the chunk thread).
 */
public final class ChunkTicketHolds implements ChunkHoldController {
    private final ServerLevel level;

    public ChunkTicketHolds(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void acquire(int chunkX, int chunkZ) {
        level.getChunkSource().ticketStorage.addTicket(new Ticket(LeafsTicketTypes.hold, ChunkLevel.MAX_LEVEL), new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void release(int chunkX, int chunkZ) {
        level.getChunkSource().ticketStorage.removeTicket(new Ticket(LeafsTicketTypes.hold, ChunkLevel.MAX_LEVEL), new ChunkPos(chunkX, chunkZ));
    }
}
