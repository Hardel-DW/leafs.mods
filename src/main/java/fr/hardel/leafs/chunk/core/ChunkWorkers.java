package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.Leafs;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server's chunk progression pool: it runs the generation layer resumptions, the disk reads and
 * the drain reactions that vanilla funneled through one consecutive worldgen lane. Priorities live in
 * the dispatcher's queue, so the pool itself is plain FIFO threads. Thread count is the one real
 * arbitration knob between region ticks and generation, {@code chunk_threads} in the config.
 */
public final class ChunkWorkers implements Executor {

    private final ThreadPoolExecutor pool;
    private final int threads;
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();

    public ChunkWorkers(int threads) {
        this.threads = threads;
        AtomicInteger ids = new AtomicInteger(1);
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "Leafs Chunk Worker #" + ids.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, throwable) -> Leafs.LOGGER.error("Uncaught exception on {}", t.getName(), throwable));
            workerThreads.add(thread);
            return thread;
        });
    }

    public int threads() {
        return threads;
    }

    public List<Thread> workerThreads() {
        return Collections.unmodifiableList(workerThreads);
    }

    public int queuedTasks() {
        return pool.getQueue().size();
    }

    public int activeWorkers() {
        return pool.getActiveCount();
    }

    @Override
    public void execute(Runnable task) {
        pool.execute(task);
    }

    /** Called after the final world save, whose futures the pool must still be alive to complete. */
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                Leafs.LOGGER.warn("Chunk workers still busy after 10 s, abandoning {} queued tasks", pool.shutdownNow().size());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
