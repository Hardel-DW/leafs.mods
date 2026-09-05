package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;

/** What follows from where the players stand: the view and simulation tickets, the spawn disk, the distance of a chunk to the nearest player. */
public final class PlayerView {
    private static final int SPAWN_RADIUS = 8;
    private static final int DEFAULT_SIMULATION_DISTANCE = 10;
    private final TicketGraphs graphs;
    private final ChunkLevels players;
    private final PlayerSources sources;
    private final ViewTickets tickets;

    public PlayerView(TicketStorage storage, TicketGraphs graphs) {
        this.graphs = graphs;
        this.players = graphs.players();
        this.sources = new PlayerSources(storage, players, DEFAULT_SIMULATION_DISTANCE);
        this.tickets = new ViewTickets(storage, players, 0);
    }

    /** The view tickets follow the players graph. */
    public LevelListener tickets() {
        return tickets;
    }

    /** One write among the others of the batch: a move leaves and enters before any graph drains. */
    public void enter(long chunkKey) {
        graphs.batch(() -> sources.enter(chunkKey));
    }

    public void leave(long chunkKey) {
        graphs.batch(() -> sources.leave(chunkKey));
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
}
