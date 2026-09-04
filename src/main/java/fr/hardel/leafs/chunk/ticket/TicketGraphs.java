package fr.hardel.leafs.chunk.ticket;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** The two graphs the tickets feed. Only the writer of a simulation ticket drains the simulation graph, in its own tick; the pool drains the loading graph. */
public final class TicketGraphs {
    private static final int LEVELS = ChunkLevel.MAX_LEVEL + 2;

    private final ChunkLevels loading = new ChunkLevels(LEVELS);
    private final ChunkLevels simulation = new ChunkLevels(LEVELS);
    private final ThreadLocal<Boolean> batching = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> wroteSimulation = ThreadLocal.withInitial(() -> false);
    private final AtomicBoolean handed = new AtomicBoolean();
    private volatile ChunkPool pool;
    private volatile LevelListener loadingListener;
    private volatile LevelListener simulationListener;

    public ChunkLevels loading() {
        return loading;
    }

    public ChunkLevels simulation() {
        return simulation;
    }

    public void listen(LevelListener loading, LevelListener simulation, ChunkPool pool) {
        this.loadingListener = loading;
        this.simulationListener = simulation;
        this.pool = pool;
    }

    public TicketStorage.ChunkUpdated loadingFeed() {
        return (key, level, _) -> loading.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
    }

    public TicketStorage.ChunkUpdated simulationFeed() {
        return (key, level, _) -> {
            simulation.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
            wroteSimulation.set(true);
        };
    }

    /** Several ticket writes, one drain at the end, once every monitor is released. */
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

    /** True when a level changed on this thread. Off the pool, one loading pass is handed over at a time. */
    public boolean drain() {
        if (loadingListener == null || batching.get()) {
            return false;
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

    /** Ticket-writing work on the pool, drained once done. */
    public void onPool(Runnable work) {
        if (ChunkPool.isWorker()) {
            batch(work);
            return;
        }

        pool.execute(() -> batch(work));
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
