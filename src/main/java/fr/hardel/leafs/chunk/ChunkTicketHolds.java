package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class ChunkTicketHolds implements ChunkHoldController {
    private final ServerLevel level;

    public ChunkTicketHolds(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void addHold(int chunkX, int chunkZ, MailHold.Level level) {
        this.level.getChunkSource().ticketStorage.addTicket(ticket(level), new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void removeHold(int chunkX, int chunkZ, MailHold.Level level) {
        this.level.getChunkSource().ticketStorage.removeTicket(ticket(level), new ChunkPos(chunkX, chunkZ));
    }

    private static Ticket ticket(MailHold.Level level) {
        int ticketLevel = switch (level) {
            case LOADED -> ChunkLevel.MAX_LEVEL;
            case FULL -> ChunkLevel.byStatus(ChunkStatus.FULL);
        };
        return new Ticket(LeafsTicketTypes.hold, ticketLevel);
    }
}
