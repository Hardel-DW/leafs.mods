package fr.hardel.leafs.chunk.ticket;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.ticking.TickEpochs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TicketGraphs {
    private static final int LEVELS = ChunkLevel.MAX_LEVEL + 2;

    private final ChunkLevels loading;
    private final ChunkLevels simulation;
    private final ChunkLevels players;
    private volatile List<Drain> drains = List.of();

    public TicketGraphs(TickEpochs epochs) {
        this.loading = new ChunkLevels(LEVELS, epochs);
        this.simulation = new ChunkLevels(LEVELS, epochs);
        this.players = new ChunkLevels(ChunkMap.MAX_VIEW_DISTANCE + 2, epochs);
    }

    public ChunkLevels loading() {
        return loading;
    }

    // Used by the Leafs Debug mod
    public ChunkLevels simulation() {
        return simulation;
    }

    public int sectionCount() {
        return loading.sectionCount() + simulation.sectionCount() + players.sectionCount();
    }

    public ChunkLevels players() {
        return players;
    }

    public void listen(Supplier<LevelListener> loading, LevelListener simulation, LevelListener players, ChunkPool pool) {
        this.drains = List.of(new Drain(this.players, () -> players, pool), new Drain(this.simulation, () -> simulation, pool), new Drain(this.loading, loading, pool));
    }

    public void drain() {
        for (Drain drain : drains) {
            drain.request();
        }
    }

    // One pool task per graph at a time; a drain that published asks again, since its listener may have written the other graphs.
    private final class Drain {
        private final ChunkLevels graph;
        private final Supplier<LevelListener> listener;
        private final ChunkPool pool;
        private final AtomicBoolean handed = new AtomicBoolean();

        private Drain(ChunkLevels graph, Supplier<LevelListener> listener, ChunkPool pool) {
            this.graph = graph;
            this.listener = listener;
            this.pool = pool;
        }

        private void request() {
            if (!graph.dirty() || !handed.compareAndSet(false, true)) {
                return;
            }

            pool.execute(() -> {
                handed.set(false);
                if (graph.drain(listener.get())) {
                    drain();
                }
            });
        }
    }
}
