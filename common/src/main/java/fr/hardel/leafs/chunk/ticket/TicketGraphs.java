package fr.hardel.leafs.chunk.ticket;

import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TicketGraphs {
    private static final int LEVELS = ChunkLevel.MAX_LEVEL + 2;

    private final ChunkLevels loading = new ChunkLevels(LEVELS);
    private final ChunkLevels simulation = new ChunkLevels(LEVELS);
    private final ChunkLevels players = new ChunkLevels(ChunkMap.MAX_VIEW_DISTANCE + 2);
    private final ThreadLocal<Boolean> batching = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> wroteSimulation = ThreadLocal.withInitial(() -> false);
    private final ConcurrentLongSet written = new ConcurrentLongSet();
    private final AtomicBoolean handed = new AtomicBoolean();
    private volatile ChunkPool pool;
    private volatile Supplier<LevelListener> loadingListener;
    private volatile LevelListener simulationListener;
    private volatile LevelListener playersListener;

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

    public TicketStorage.ChunkUpdated loadingFeed() {
        return (key, level, added) -> {
            loading.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
            if (added && !ChunkLevels.draining() && !ChunkPool.isWorker()) {
                written.add(key);
            }
        };
    }

    public void settleWritten() {
        for (LongIterator keys = written.iterator(); keys.hasNext(); ) {
            long key = keys.nextLong();
            keys.remove();
            loading.settled(ChunkPos.getX(key), ChunkPos.getZ(key), loadingListener.get(), () -> null);
        }
    }

    public TicketStorage.ChunkUpdated simulationFeed() {
        return (key, level, _) -> {
            simulation.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
            wroteSimulation.set(true);
        };
    }

    public void batch(Runnable writes) {
        batch(() -> {
            writes.run();
            return null;
        });
    }

    public <T> T batch(Supplier<T> writes) {
        if (batching.get()) {
            return writes.get();
        }

        T result;
        batching.set(true);
        try {
            result = writes.get();
        } finally {
            batching.set(false);
        }

        drain();
        return result;
    }

    public boolean drain() {
        if (loadingListener == null || batching.get()) {
            return false;
        }

        batching.set(true);
        try {
            players.drain(playersListener);
        } finally {
            batching.set(false);
        }

        boolean changed = drainOwnSimulation();
        if (ChunkPool.isWorker()) {
            return loading.drain(loadingListener.get()) | changed;
        }

        if (handed.compareAndSet(false, true)) {
            pool.execute(() -> {
                handed.set(false);
                loading.drain(loadingListener.get());
            });
        }

        return changed;
    }

    private boolean drainOwnSimulation() {
        if (!wroteSimulation.get()) {
            return false;
        }

        wroteSimulation.set(false);
        return simulation.drain(simulationListener);
    }
}
