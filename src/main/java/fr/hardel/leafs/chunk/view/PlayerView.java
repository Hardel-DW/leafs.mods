package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;

/** Where the players are and what follows from it: the players graph, the view and simulation tickets, the spawn disk, the urgency of a chunk. */
public final class PlayerView {
    private static final int SPAWN_RADIUS = 8;
    private static final int DEFAULT_SIMULATION_DISTANCE = 10;
    private final ChunkLevels players = new ChunkLevels(ChunkMap.MAX_VIEW_DISTANCE + 2);
    private final TicketGraphs graphs;
    private final PlayerSources sources;
    private final ViewTickets tickets;

    public PlayerView(TicketStorage storage, TicketGraphs graphs) {
        this.graphs = graphs;
        this.sources = new PlayerSources(storage, players, DEFAULT_SIMULATION_DISTANCE);
        this.tickets = new ViewTickets(storage, players, 0);
    }

    public void enter(long chunkKey) {
        sources.enter(chunkKey);
        drain();
    }

    public void leave(long chunkKey) {
        sources.leave(chunkKey);
        drain();
    }

    public void viewDistance(int distance) {
        graphs.batch(() -> tickets.viewDistance(distance));
    }

    public void simulationDistance(int distance) {
        sources.simulationDistance(distance);
    }

    /** Vanilla's queue level: the distance to the nearest player, past the view when none is near. */
    public int level(int chunkX, int chunkZ) {
        return players.level(ChunkPos.pack(chunkX, chunkZ));
    }

    /** Within 8 chunks of a player, the census condition of the spawn pass. */
    public boolean covered(long chunkKey) {
        return players.level(chunkKey) <= SPAWN_RADIUS;
    }

    /** Vanilla's TriState: TRUE inside the inscribed square, FALSE past 8, DEFAULT asks the exact euclidean test. */
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

    private void drain() {
        graphs.onPool(() -> players.drain(tickets));
    }
}
