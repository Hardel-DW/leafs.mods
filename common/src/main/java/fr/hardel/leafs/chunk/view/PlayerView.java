package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;

import java.util.function.LongPredicate;

public final class PlayerView {
    private static final int SPAWN_RADIUS = 8;
    private static final int DEFAULT_SIMULATION_DISTANCE = 10;
    private final TicketGraphs graphs;
    private final ChunkLevels players;
    private final PlayerSources sources;
    private final ViewTickets tickets;

    public PlayerView(TicketStorage storage, TicketGraphs graphs, LongPredicate full, int loads) {
        this.graphs = graphs;
        this.players = graphs.players();
        this.sources = new PlayerSources(storage, players, DEFAULT_SIMULATION_DISTANCE);
        this.tickets = new ViewTickets(storage, players, 0, full, loads);
    }

    public LevelListener tickets() {
        return tickets;
    }

    public void enter(long chunkKey) {
        sources.enter(chunkKey);
    }

    public void leave(long chunkKey) {
        sources.leave(chunkKey);
    }

    public void arrived(long chunkKey) {
        tickets.arrived(chunkKey);
    }

    public void viewDistance(int distance) {
        players.exclusive(() -> tickets.viewDistance(distance));
    }

    public void simulationDistance(int distance) {
        sources.simulationDistance(distance);
    }

    public int urgency(int chunkX, int chunkZ) {
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        if (!ChunkLevel.isLoaded(graphs.loading().level(chunkKey))) {
            return ChunkPool.SECOND;
        }

        int seen = players.level(chunkKey);
        int depth = seen == players.none() ? graphs.loading().level(chunkKey) - ChunkLevel.byStatus(FullChunkStatus.FULL) + tickets.viewDistance() : seen;
        return ChunkPool.THIRD + Math.clamp(depth, 0, tickets.viewDistance());
    }

    public TriState nearby(long chunkKey) {
        int distance = players.level(chunkKey);
        if (distance <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK) {
            return TriState.TRUE;
        }

        return distance > SPAWN_RADIUS ? TriState.FALSE : TriState.DEFAULT;
    }

    public int spawnChunkCount() {
        int[] count = new int[1];
        players.forEachAtMost(SPAWN_RADIUS, _ -> count[0]++);
        return count[0];
    }

    public LongIterator spawnCandidates() {
        LongArrayList candidates = new LongArrayList();
        players.forEachAtMost(SPAWN_RADIUS, candidates::add);
        return candidates.iterator();
    }
}
