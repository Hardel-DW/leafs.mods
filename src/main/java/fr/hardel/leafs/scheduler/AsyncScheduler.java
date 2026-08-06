package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.Leafs;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Fire-and-forget pool for work with no world access (I/O, computations). */
public final class AsyncScheduler {
    private final ExecutorService pool;

    public AsyncScheduler(int threads) {
        AtomicInteger threadId = new AtomicInteger(1);
        this.pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "Leafs Async Worker #" + threadId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void run(Runnable task) {
        pool.execute(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                Leafs.LOGGER.error("Async task failed", throwable);
            }
        });
    }

    public boolean shutdown(Duration timeout) {
        pool.shutdown();
        try {
            if (pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        pool.shutdownNow();

        return false;
    }
}
