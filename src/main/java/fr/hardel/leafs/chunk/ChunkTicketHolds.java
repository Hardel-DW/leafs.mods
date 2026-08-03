package fr.hardel.leafs.chunk;

import fr.hardel.leafs.scheduler.ChunkHoldController;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/**
 * The real {@link ChunkHoldController}: one HOLD ticket at holder-alive level per held chunk. The
 * refcount lives in scheduler/SharedChunkHolds, so add/remove pair exactly one ticket - which is also
 * what vanilla allows, since it deduplicates tickets by (type, level). Must be called on the thread
 * that owns the level's ticket storage, because the add fires the loading tracker inline.
 */
public final class ChunkTicketHolds implements ChunkHoldController {
    private final ServerLevel level;

    public ChunkTicketHolds(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void addHold(int chunkX, int chunkZ) {
        level.getChunkSource().ticketStorage.addTicket(new Ticket(LeafsTicketTypes.hold, ChunkLevel.MAX_LEVEL), new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void removeHold(int chunkX, int chunkZ) {
        level.getChunkSource().ticketStorage.removeTicket(new Ticket(LeafsTicketTypes.hold, ChunkLevel.MAX_LEVEL), new ChunkPos(chunkX, chunkZ));
    }
}
