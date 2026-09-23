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

    public LevelListener tickets() {
        return tickets;
    }

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

    public int urgency(int chunkX, int chunkZ) {
        return Math.min(players.level(ChunkPos.pack(chunkX, chunkZ)), tickets.viewDistance());
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
