package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Region thread pool at 20 TPS: one due handle per poll, one tick per pass. A late handle is
 * rescheduled from now, so a lagging region runs fewer ticks per second instead of catching up.
 * {@link #runAttached} runs the same tick path on the calling thread.
 */
public final class RegionTickScheduler {
    public static final long TICK_PERIOD_NANOS = 50_000_000L;

    private final DelayQueue<ScheduledTick> queue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final int threadCount;
    private final boolean regionThreadNames;
    private final TickBarrier barrier;
    private final LeafsWatchdog watchdog;
    private final RegionCrashWriter crashWriter;
    private final BiConsumer<TickHandle, Throwable> failurePolicy;
    private volatile long periodNanos = TICK_PERIOD_NANOS;
    private volatile boolean running = true;

    public RegionTickScheduler(int threadCount, boolean regionThreadNames, TickBarrier barrier, LeafsWatchdog watchdog, RegionCrashWriter crashWriter, BiConsumer<TickHandle, Throwable> failurePolicy) {
        this.threadCount = threadCount;
        this.regionThreadNames = regionThreadNames;
        this.barrier = barrier;
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

    /** Waits the workers out so a mid-flight tick can finish releasing its region before the drain runs. */
    public void shutdown() {
        running = false;
        workers.forEach(Thread::interrupt);
        for (Thread worker : workers) {
            try {
                worker.join(5_000);
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

    /** From the tick-rate manager; handles pick the new period up at their next scheduling. */
    public void setPeriodNanos(long periodNanos) {
        this.periodNanos = Math.max(1, periodNanos);
    }

    public void runAttached(TickHandle handle) {
        executeTick(handle);
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

    /**
     * Each acquisition is paired with its own {@code finally} so a failed entry can never strand an
     * active tick and block a later barrier raise. The crash report is built before the context exits.
     */
    private void executeTick(TickHandle handle) {
        barrier.enterTick();
        try {
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
        } finally {
            barrier.exitTick();
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
