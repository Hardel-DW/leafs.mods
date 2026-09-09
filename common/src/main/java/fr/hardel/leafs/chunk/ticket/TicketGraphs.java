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

/** The three graphs. The writer drains the players graph, whose tickets feed the two others, and its own simulation writes; the pool drains the loading graph. */
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
    private volatile LevelListener loadingListener;
    private volatile LevelListener simulationListener;
    private volatile LevelListener playersListener;

    public ChunkLevels loading() {
        return loading;
    }

    public ChunkLevels simulation() {
        return simulation;
    }

    /** The sections the three graphs hold, what grows if levels are never forgotten. */
    public int sectionCount() {
        return loading.sectionCount() + simulation.sectionCount() + players.sectionCount();
    }

    /** Where the players stand, one level per chunk of distance to the nearest. */
    public ChunkLevels players() {
        return players;
    }

    public void listen(LevelListener loading, LevelListener simulation, LevelListener players, ChunkPool pool) {
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

    public void settleWritten(LevelListener listener) {
        for (LongIterator keys = written.iterator(); keys.hasNext(); ) {
            long key = keys.nextLong();
            keys.remove();
            loading.settled(ChunkPos.getX(key), ChunkPos.getZ(key), listener, () -> null);
        }
    }

    public TicketStorage.ChunkUpdated simulationFeed() {
        return (key, level, _) -> {
            simulation.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
            wroteSimulation.set(true);
        };
    }

    /** Several writes, one drain at the end, once every monitor is released. */
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

    /** The players drain writes tickets, so it runs as one more batch; off the pool, one loading pass is handed over at a time. */
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
            return loading.drain(loadingListener) | changed;
        }

        if (handed.compareAndSet(false, true)) {
            pool.execute(() -> {
                handed.set(false);
                loading.drain(loadingListener);
            });
        }

        return changed;
    }

    /** A bystander leaves a region's pending move alone. */
    private boolean drainOwnSimulation() {
        if (!wroteSimulation.get()) {
            return false;
        }

        wroteSimulation.set(false);
        return simulation.drain(simulationListener);
    }
}
