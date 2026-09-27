package fr.hardel.leafs.chunk.view;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.ticking.TickEpochs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class ViewTicketsTest {
    private static final int PLAYER_LEVELS = 34;
    private final TicketStorage tickets = new TicketStorage();
    private final ChunkLevels players = new ChunkLevels(PLAYER_LEVELS, new TickEpochs(0));
    private final ViewTickets view = new ViewTickets(tickets, players, 2);
    private final PlayerSources sources = new PlayerSources(tickets, players, 5);

    private boolean holds(int chunkX, int chunkZ, TicketType type) {
        return tickets.getTickets(ChunkPos.pack(chunkX, chunkZ)).stream().anyMatch(ticket -> ticket.getType() == type);
    }

    private int levelOf(int chunkX, int chunkZ, TicketType type) {
        return tickets.getTickets(ChunkPos.pack(chunkX, chunkZ)).stream().filter(ticket -> ticket.getType() == type).mapToInt(Ticket::getTicketLevel).findFirst().orElseThrow();
    }

    @Test
    void aPlayerTicketsHisViewSquareAndHisChunk() {
        sources.enter(ChunkPos.pack(5, 5));
        players.drain(view);

        assertTrue(holds(5, 5, TicketType.PLAYER_LOADING));
        assertTrue(holds(7, 7, TicketType.PLAYER_LOADING));
        assertFalse(holds(8, 5, TicketType.PLAYER_LOADING));
        assertEquals(31, levelOf(5, 5, TicketType.PLAYER_LOADING));
        assertTrue(holds(5, 5, TicketType.PLAYER_SIMULATION));
        assertEquals(26, levelOf(5, 5, TicketType.PLAYER_SIMULATION));
        assertFalse(holds(6, 5, TicketType.PLAYER_SIMULATION));
    }

    @Test
    void theLastPlayerLeavingClearsTheSquare() {
        sources.enter(ChunkPos.pack(5, 5));
        sources.enter(ChunkPos.pack(5, 5));
        players.drain(view);

        sources.leave(ChunkPos.pack(5, 5));
        players.drain(view);
        assertTrue(holds(7, 7, TicketType.PLAYER_LOADING));

        sources.leave(ChunkPos.pack(5, 5));
        players.drain(view);
        assertFalse(holds(7, 7, TicketType.PLAYER_LOADING));
        assertFalse(holds(5, 5, TicketType.PLAYER_SIMULATION));
    }

    @Test
    void aWiderViewDistanceTicketsTheNewRing() {
        sources.enter(ChunkPos.pack(5, 5));
        players.drain(view);

        view.viewDistance(3);

        assertTrue(holds(8, 5, TicketType.PLAYER_LOADING));
        assertFalse(holds(9, 5, TicketType.PLAYER_LOADING));
        view.viewDistance(1);
        assertFalse(holds(8, 5, TicketType.PLAYER_LOADING));
        assertFalse(holds(7, 7, TicketType.PLAYER_LOADING));
        assertTrue(holds(6, 6, TicketType.PLAYER_LOADING));
    }

    @Test
    void aNewSimulationDistanceMovesTheLevel() {
        sources.enter(ChunkPos.pack(5, 5));

        sources.simulationDistance(10);

        assertEquals(21, levelOf(5, 5, TicketType.PLAYER_SIMULATION));
    }

    /** 2026-09-27: a player crossing into the next graph section dropped and retook his whole view in one drain. */
    @Test
    void aPlayerCrossingASectionOnlyDropsTheRowHeLeaves() {
        sources.enter(ChunkPos.pack(63, 0));
        players.drain(view);
        Set<Long> dropped = new HashSet<>();
        tickets.setLoadingChunkUpdatedListener((key, level, _) -> {
            if (!ChunkLevel.isLoaded(level)) {
                dropped.add(key);
            }
        });

        sources.leave(ChunkPos.pack(63, 0));
        sources.enter(ChunkPos.pack(64, 0));
        players.drain(view);

        assertEquals(IntStream.rangeClosed(-2, 2).mapToObj(chunkZ -> ChunkPos.pack(61, chunkZ)).collect(Collectors.toSet()), dropped);
    }
}
