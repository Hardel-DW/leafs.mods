package fr.hardel.leafs.chunk.pool;

import fr.hardel.leafs.Leafs;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Every piece of chunk work runs here, most urgent first, under its reservation. Lowest OS priority so region ticks win the cores. */
public final class ChunkPool implements Executor {
    private static final long[] NO_RESERVATION = {};

    private final PriorityBuckets buckets;
    private final Reservations reservations = new Reservations(this::enqueue);
    private final Semaphore permits = new Semaphore(0);
    private final List<Thread> workers;
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean running = true;

    public ChunkPool(int threads, int priorities) {
        this.buckets = new PriorityBuckets(priorities);
        List<Thread> started = new ArrayList<>(threads);
        for (int index = 1; index <= threads; index++) {
            Thread worker = new Thread(this::work, "Leafs Chunk Worker #" + index);
            worker.setDaemon(true);
            started.add(worker);
            worker.start();
        }

        this.workers = List.copyOf(started);
    }

    /** The lowest priority, Java's everywhere and the OS nice on Linux, for any thread that generates or saves chunks. */
    public static void yieldToRegions() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        NativeThreadPriority.lowerCurrentThread();
    }

    public int threads() {
        return workers.size();
    }

    public List<Thread> workerThreads() {
        return workers;
    }

    /** In a bucket or parked. */
    public int queued() {
        return queued.get();
    }

    public int active() {
        return active.get();
    }

    public void submit(ChunkTask task) {
        queued.incrementAndGet();
        enqueue(task);
    }

    /** A running task is unaffected. */
    public void reprioritise(ChunkTask task, int priority) {
        task.wants(priority);
        if (buckets.move(task, priority)) {
            permits.release();
        }
    }

    /** Plain work, no reservation, the least urgent. */
    @Override
    public void execute(@NonNull Runnable task) {
        submit(ChunkTask.of(buckets.count() - 1, NO_RESERVATION, task));
    }

    /** Queued work still runs, within ten seconds. */
    public void shutdown() {
        running = false;
        permits.release();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (Thread worker : workers) {
            try {
                worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (queued.get() > 0) {
            Leafs.LOGGER.warn("Chunk pool still busy after 10 s, abandoning {} queued tasks", queued.get());
        }
    }

    private void enqueue(ChunkTask task) {
        buckets.add(task);
        permits.release();
    }

    /** The released permit wakes the next sleeper, so every worker leaves. */
    private void work() {
        yieldToRegions();
        while (true) {
            permits.acquireUninterruptibly();
            ChunkTask task = buckets.poll();
            if (task == null) {
                if (running || queued.get() > 0) {
                    continue;
                }

                permits.release();
                return;
            }

            if (!reservations.acquire(task)) {
                continue;
            }

            queued.decrementAndGet();
            active.incrementAndGet();
            runReserved(task);
        }
    }

    private void runReserved(ChunkTask task) {
        @Nullable CompletableFuture<?> pending;
        try {
            pending = task.run();
        } catch (Throwable failure) {
            finish(task);
            Leafs.LOGGER.error("Chunk task failed on {}", Thread.currentThread().getName(), failure);
            return;
        }

        if (pending == null) {
            finish(task);
            return;
        }

        pending.whenComplete((_, _) -> finish(task));
    }

    private void finish(ChunkTask task) {
        active.decrementAndGet();
        reservations.release(task);
    }
}
