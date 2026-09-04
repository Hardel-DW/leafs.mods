package fr.hardel.leafs.chunk.ticket;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.function.Supplier;

/** The two graphs the tickets feed. Whoever writes a ticket drains once the write is out, unless it opened a batch. */
public final class TicketGraphs {
    private static final int LEVELS = ChunkLevel.MAX_LEVEL + 2;

    private final ChunkLevels loading = new ChunkLevels(LEVELS);
    private final ChunkLevels simulation = new ChunkLevels(LEVELS);
    private final ThreadLocal<Boolean> batching = ThreadLocal.withInitial(() -> false);
    private volatile LevelListener loadingListener;
    private volatile LevelListener simulationListener;

    public ChunkLevels loading() {
        return loading;
    }

    public ChunkLevels simulation() {
        return simulation;
    }

    public void listen(LevelListener loading, LevelListener simulation) {
        this.loadingListener = loading;
        this.simulationListener = simulation;
    }

    public TicketStorage.ChunkUpdated loadingFeed() {
        return (key, level, _) -> loading.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
    }

    public TicketStorage.ChunkUpdated simulationFeed() {
        return (key, level, _) -> simulation.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level);
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

    /** True when any level changed. Nothing before the listeners exist or inside a batch. */
    public boolean drain() {
        LevelListener holders = loadingListener;
        if (holders == null || batching.get()) {
            return false;
        }

        boolean changed = loading.drain(holders);
        return simulation.drain(simulationListener) | changed;
    }
}
