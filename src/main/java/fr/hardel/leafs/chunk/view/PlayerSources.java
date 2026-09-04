package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Where the players stand: a source in the players graph and one PLAYER_SIMULATION ticket per occupied chunk, whoever stands there. */
public final class PlayerSources {
    private final TicketStorage tickets;
    private final ChunkLevels players;
    private final Long2IntOpenHashMap occupants = new Long2IntOpenHashMap();
    private int simulationLevel;

    public PlayerSources(TicketStorage tickets, ChunkLevels players, int simulationDistance) {
        this.tickets = tickets;
        this.players = players;
        this.simulationLevel = level(simulationDistance);
    }

    public synchronized void enter(long chunkKey) {
        if (occupants.addTo(chunkKey, 1) == 0) {
            players.setSource(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), 0);
            tickets.addTicket(chunkKey, ticket());
        }
    }

    public synchronized void leave(long chunkKey) {
        if (occupants.addTo(chunkKey, -1) == 1) {
            occupants.remove(chunkKey);
            players.setSource(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), players.none());
            tickets.removeTicket(chunkKey, ticket());
        }
    }

    public synchronized void simulationDistance(int distance) {
        simulationLevel = level(distance);
        tickets.replaceTicketLevelOfType(simulationLevel, TicketType.PLAYER_SIMULATION);
    }

    private static int level(int simulationDistance) {
        return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - simulationDistance);
    }

    private Ticket ticket() {
        return new Ticket(TicketType.PLAYER_SIMULATION, simulationLevel);
    }
}
