package fr.hardel.leafs.ticking;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Region worker pool, one tick per pass, a late region restarts from now instead of catching up. */
public final class RegionTickScheduler {
    public static final long TICK_PERIOD_NANOS = 50_000_000L;

    private final DelayQueue<ScheduledTick> queue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final int threadCount;
    private final boolean regionThreadNames;
    private final LeafsWatchdog watchdog;
    private final RegionCrashWriter crashWriter;
    private final BiConsumer<TickHandle, Throwable> failurePolicy;
    private volatile long periodNanos = TICK_PERIOD_NANOS;
    private volatile boolean running = true;

    public RegionTickScheduler(int threadCount, boolean regionThreadNames, LeafsWatchdog watchdog, RegionCrashWriter crashWriter, BiConsumer<TickHandle, Throwable> failurePolicy) {
        this.threadCount = threadCount;
        this.regionThreadNames = regionThreadNames;
        this.watchdog = watchdog;
        this.crashWriter = crashWriter;
        this.failurePolicy = failurePolicy;
    }

    public void start() {
        for (int index = 1; index <= threadCount; index++) {
            Thread worker = new Thread(this::workerLoop, "Leafs Region Worker #" + index);
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
        handle.setScheduledStartNanos(System.nanoTime() + periodNanos);
        queue.add(new ScheduledTick(handle));
    }

    public void setPeriodNanos(long periodNanos) {
        this.periodNanos = Math.max(1, periodNanos);
    }

    public long periodNanos() {
        return periodNanos;
    }

    public void runAttached(TickHandle handle) {
        executeTick(handle);
    }

    public List<Thread> workerThreads() {
        return Collections.unmodifiableList(workers);
    }

    private void workerLoop() {
        while (running) {
            ScheduledTick next;
            try {
                next = queue.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            if (next == null || next.handle.isCancelled()) {
                continue;
            }

            TickHandle handle = next.handle;
            Thread worker = Thread.currentThread();
            String workerName = worker.getName();
            if (regionThreadNames) {
                worker.setName("R#" + handle.id() + " " + handle.dimension());
            }

            try {
                executeTick(handle);
            } catch (Throwable throwable) {
                failurePolicy.accept(handle, throwable);
                continue;
            } finally {
                if (regionThreadNames) {
                    worker.setName(workerName);
                }
            }

            if (!handle.isCancelled()) {
                handle.setScheduledStartNanos(Math.max(System.nanoTime(), handle.scheduledStartNanos() + periodNanos));
                queue.add(next);
            }
        }
    }

    private void executeTick(TickHandle handle) {
        RegionContext.enter(handle.context());
        try {
            watchdog.beginTick(handle);
            handle.tick();
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

    private record ScheduledTick(TickHandle handle) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(handle.scheduledStartNanos() - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
