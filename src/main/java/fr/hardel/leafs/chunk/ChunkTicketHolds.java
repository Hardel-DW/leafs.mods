package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

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
