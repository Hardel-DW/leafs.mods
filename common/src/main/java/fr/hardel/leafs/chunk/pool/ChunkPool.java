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
import java.util.function.BiConsumer;

public final class ChunkPool implements Executor {
    public static final int FIRST = 0;
    public static final int SECOND = 1;

    private final PriorityBuckets buckets;
    private final PlacedTasks placed = new PlacedTasks();
    private final Reservations reservations = new Reservations(this::enqueue);
    private final Semaphore permits = new Semaphore(0);
    private final List<Thread> workers;
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final ReservationBlocks blocks = new ReservationBlocks();
    private final BiConsumer<ChunkTask, Throwable> failures;
    private volatile boolean running = true;

    public ChunkPool(ThreadGroup serverThreads, int threads, int priorities, BiConsumer<ChunkTask, Throwable> failures) {
        this.buckets = new PriorityBuckets(priorities);
        this.failures = failures;
        List<Thread> started = new ArrayList<>(threads);
        for (int index = 1; index <= threads; index++) {
            Thread worker = new Worker(serverThreads, this::work, index);
            worker.setDaemon(true);
            started.add(worker);
            worker.start();
        }

        this.workers = List.copyOf(started);
    }

    public static void yieldToRegions() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        NativeThreadPriority.lowerCurrentThread();
    }

    public static boolean isWorker() {
        return Thread.currentThread() instanceof Worker;
    }

    public int threads() {
        return workers.size();
    }

    // Used by the Leafs Debug mod
    public List<Thread> workerThreads() {
        return workers;
    }

    // Used by the Leafs Debug mod
    public int queued() {
        return queued.get();
    }

    // Used by the Leafs Debug mod
    public ReservationBlocks blocks() {
        return blocks;
    }

    // Used by the Leafs Debug mod
    public int active() {
        return active.get();
    }

    public void submit(ChunkTask task) {
        queued.incrementAndGet();
        placed.add(task);
        enqueue(task);
    }

    public void changed(long key) {
        placed.forEachAt(key, task -> reprioritise(task, task.place().priority()));
    }

    public void expedite(long key) {
        placed.forEachAt(key, task -> reprioritise(task, FIRST));
    }

    public int queuedAt(long key) {
        return placed.countAt(key);
    }

    private void reprioritise(ChunkTask task, int priority) {
        task.wants(priority);
        if (buckets.move(task, priority)) {
            permits.release();
        }
    }

    @Override
    public void execute(@NonNull Runnable task) {
        submit(ChunkTask.of(ChunkTask.Kind.HOUSEKEEPING, SECOND, ChunkTask.NO_RESERVATION, task));
    }

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

            if (task.withdrawn()) {
                placed.remove(task);
                queued.decrementAndGet();
                continue;
            }

            ChunkTask holder = reservations.acquire(task);
            if (holder != null) {
                blocks.count(task, holder);
                continue;
            }

            placed.remove(task);
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
            failures.accept(task, failure);
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

    private static final class Worker extends Thread {
        private Worker(ThreadGroup group, Runnable work, int index) {
            super(group, work, "Leafs Chunk Worker #%s".formatted(index));
        }
    }
}
