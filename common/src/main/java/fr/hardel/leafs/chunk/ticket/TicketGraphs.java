package fr.hardel.leafs.chunk.ticket;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.ticking.TickEpochs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TicketGraphs {
    private static final int LEVELS = ChunkLevel.MAX_LEVEL + 2;

    private final ChunkLevels loading;
    private final ChunkLevels simulation;
    private final ChunkLevels players;
    private final AtomicBoolean handed = new AtomicBoolean();
    private volatile ChunkPool pool;
    private volatile Supplier<LevelListener> loadingListener;
    private volatile LevelListener simulationListener;
    private volatile LevelListener playersListener;

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
        this.loadingListener = loading;
        this.simulationListener = simulation;
        this.playersListener = players;
        this.pool = pool;
    }

    public boolean drain() {
        if (loadingListener == null) {
            return false;
        }

        players.drain(playersListener);
        boolean changed = simulation.drain(simulationListener);
        if (handed.compareAndSet(false, true)) {
            pool.execute(() -> {
                handed.set(false);
                loading.drain(loadingListener.get());
            });
        }

        return changed;
    }
}
