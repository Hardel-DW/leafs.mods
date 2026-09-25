package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

public final class ViewTickets implements LevelListener {
    private static final int LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    private final TicketStorage tickets;
    private final ChunkLevels players;
    private volatile int viewDistance;

    public ViewTickets(TicketStorage tickets, ChunkLevels players, int viewDistance) {
        this.tickets = tickets;
        this.players = players;
        this.viewDistance = viewDistance;
    }

    public int viewDistance() {
        return viewDistance;
    }

    public void viewDistance(int distance) {
        int previous = viewDistance;
        viewDistance = distance;
        if (distance > previous) {
            players.forEachAtMost(distance, chunkKey -> addBeyond(chunkKey, previous));
        }

        if (distance < previous) {
            players.forEachAtMost(previous, chunkKey -> removeBeyond(chunkKey, distance));
        }
    }

    @Override
    public void changed(long chunkKey, int oldLevel, int newLevel) {
        boolean saw = oldLevel <= viewDistance;
        boolean sees = newLevel <= viewDistance;
        if (sees && !saw) {
            tickets.addTicket(chunkKey, ticket());
        }

        if (saw && !sees) {
            tickets.removeTicket(chunkKey, ticket());
        }
    }

    private void addBeyond(long chunkKey, int distance) {
        if (players.level(chunkKey) > distance) {
            tickets.addTicket(chunkKey, ticket());
        }
    }

    private void removeBeyond(long chunkKey, int distance) {
        if (players.level(chunkKey) > distance) {
            tickets.removeTicket(chunkKey, ticket());
        }
    }

    private static Ticket ticket() {
        return new Ticket(TicketType.PLAYER_LOADING, LEVEL);
    }
}
