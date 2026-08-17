package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Region thread pool at 20 TPS: a late handle advances its clock by the missed periods instead of
 * catching up tick by tick. {@link #runAttached} runs the same tick path on the calling thread.
 */
public final class RegionTickScheduler {
    public static final long TICK_PERIOD_NANOS = 50_000_000L;

    private final DelayQueue<ScheduledTick> queue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final ConcurrentHashMap<Thread, TickHandle> activeByWorker = new ConcurrentHashMap<>();
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
        executeTick(handle, 1);
    }

    public List<Thread> workerThreads() {
        return Collections.unmodifiableList(workers);
    }

    /** The handle a worker is ticking right now, null when it idles. */
    public TickHandle activeHandle(Thread worker) {
        return activeByWorker.get(worker);
    }

    static long computeTickCount(long idealStartNanos, long nowNanos, long periodNanos) {
        return Math.max(1, 1 + (nowNanos - idealStartNanos) / periodNanos);
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
            long period = periodNanos;
            long now = System.nanoTime();
            long tickCount = computeTickCount(handle.scheduledStartNanos(), now, period);
            Thread worker = Thread.currentThread();
            String workerName = worker.getName();
            if (regionThreadNames) {
                worker.setName("R#" + handle.id() + " " + handle.dimension());
            }

            activeByWorker.put(worker, handle);
            try {
                executeTick(handle, tickCount);
            } catch (Throwable throwable) {
                failurePolicy.accept(handle, throwable);
                continue;
            } finally {
                activeByWorker.remove(worker);
                if (regionThreadNames) {
                    worker.setName(workerName);
                }
            }

            if (!handle.isCancelled()) {
                long idealNext = handle.scheduledStartNanos() + tickCount * period;
                handle.setScheduledStartNanos(Math.max(System.nanoTime(), idealNext));
                queue.add(next);
            }
        }
    }

    /**
     * Each acquisition is paired with its own {@code finally} so a failed entry can never strand an
     * active tick and block a later barrier raise. The crash report is built before the context exits.
     */
    private void executeTick(TickHandle handle, long tickCount) {
        barrier.enterTick();
        try {
            RegionContext.enter(handle.context());
            long start = System.nanoTime();
            try {
                watchdog.beginTick(handle);
                handle.tick(tickCount);
                handle.advance(tickCount);
            } catch (Throwable throwable) {
                try {
                    crashWriter.write(handle.buildCrashReport(), throwable);
                } catch (Throwable reportFailure) {
                    throwable.addSuppressed(reportFailure);
                }

                throw throwable;
            } finally {
                long end = System.nanoTime();
                handle.timings().record(end, end - start);
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
