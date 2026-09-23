package fr.hardel.leafs.ticking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

public final class RegionTickScheduler {
    private final DelayQueue<ScheduledTick> queue = new DelayQueue<>();
    private final Queue<ScheduledTick> missed = new ConcurrentLinkedQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final ThreadGroup serverThreads;
    private final int threadCount;
    private final LongSupplier periodNanos;
    private final boolean regionThreadNames;
    private final LeafsWatchdog watchdog;
    private final RegionCrashWriter crashWriter;
    private final BiConsumer<TickHandle, Throwable> failurePolicy;
    private volatile boolean running = true;

    public RegionTickScheduler(ThreadGroup serverThreads, int threadCount, LongSupplier periodNanos, boolean regionThreadNames, LeafsWatchdog watchdog, RegionCrashWriter crashWriter, BiConsumer<TickHandle, Throwable> failurePolicy) {
        this.serverThreads = serverThreads;
        this.threadCount = threadCount;
        this.periodNanos = periodNanos;
        this.regionThreadNames = regionThreadNames;
        this.watchdog = watchdog;
        this.crashWriter = crashWriter;
        this.failurePolicy = failurePolicy;
    }

    public void start() {
        for (int index = 1; index <= threadCount; index++) {
            Thread worker = new Worker(serverThreads, this::workerLoop, index);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }

    public void shutdown() {
        running = false;
        workers.forEach(Thread::interrupt);
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void schedule(TickHandle handle) {
        handle.setScheduledStartNanos(System.nanoTime() + periodNanos.getAsLong());
        queue.add(new ScheduledTick(handle));
    }

    public void wakeMissed() {
        for (ScheduledTick tick = missed.poll(); tick != null; tick = missed.poll()) {
            tick.handle.setScheduledStartNanos(System.nanoTime());
            queue.add(tick);
        }
    }

    public void runAttached(TickHandle handle) {
        executeTick(handle);
    }

    /** Vanilla and mods see a worker as the server thread. */
    public static boolean onWorker() {
        return Thread.currentThread() instanceof Worker;
    }

    // Used by the Leafs Debug mod
    public List<Thread> workerThreads() {
        return Collections.unmodifiableList(workers);
    }

    private void workerLoop() {
        while (running) {
            ScheduledTick next;
            try {
                next = queue.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            if (next.handle.isCancelled()) {
                continue;
            }

            TickHandle handle = next.handle;
            Thread worker = Thread.currentThread();
            String workerName = worker.getName();
            if (regionThreadNames) {
                worker.setName("Leafs Server R#" + handle.id() + " " + handle.dimension());
            }

            boolean started;
            try {
                started = executeTick(handle);
            } catch (Throwable throwable) {
                failurePolicy.accept(handle, throwable);
                continue;
            } finally {
                if (regionThreadNames) {
                    worker.setName(workerName);
                }
            }

            if (handle.isCancelled()) {
                continue;
            }

            if (!started) {
                handle.stages().recordMissedStart();
                missed.add(next);
                continue;
            }

            handle.setScheduledStartNanos(Math.max(System.nanoTime(), handle.scheduledStartNanos() + periodNanos.getAsLong()));
            queue.add(next);
        }
    }

    private boolean executeTick(TickHandle handle) {
        RegionContext.enter(handle.context());
        try {
            watchdog.beginTick(handle);
            return handle.tick();
        } catch (Throwable throwable) {
            try {
                crashWriter.write(handle.buildCrashReport(), throwable);
            } catch (Throwable reportFailure) {
                throwable.addSuppressed(reportFailure);
            }

            throw throwable;
        } finally {
            watchdog.endTick(handle);
            RegionContext.exit();
        }
    }

    /** The name contains "Server", the rename per tick included: some mods recognise the server thread by its name. */
    private static final class Worker extends Thread {
        private Worker(ThreadGroup group, Runnable loop, int index) {
            super(group, loop, "Leafs Server Region Worker #" + index);
        }
    }

    private record ScheduledTick(TickHandle handle) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(handle.scheduledStartNanos() - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(handle.scheduledStartNanos(), ((ScheduledTick) other).handle.scheduledStartNanos());
        }
    }
}
