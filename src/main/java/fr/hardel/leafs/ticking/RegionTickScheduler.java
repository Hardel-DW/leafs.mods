package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * The region thread pool. Free-running handles are paced at 20 TPS with Folia's catch-up model: a
 * late handle ticks once but advances its clocks by the missed periods, and a chronically late one
 * never outranks healthy ones. Until M11 the runtime drives ticks through {@link #runAttached}
 * instead — same context, crash capture and watchdog, executed on the calling thread.
 */
public final class RegionTickScheduler {
    public static final long TICK_PERIOD_NANOS = 50_000_000L;

    private final DelayQueue<ScheduledTick> queue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final TickBarrier barrier;
    private final LeafsWatchdog watchdog;
    private final RegionCrashWriter crashWriter;
    private final BiConsumer<TickHandle, Throwable> failurePolicy;
    private volatile boolean running = true;

    public RegionTickScheduler(int threadCount, TickBarrier barrier, LeafsWatchdog watchdog, RegionCrashWriter crashWriter, BiConsumer<TickHandle, Throwable> failurePolicy) {
        this.barrier = barrier;
        this.watchdog = watchdog;
        this.crashWriter = crashWriter;
        this.failurePolicy = failurePolicy;
        for (int index = 1; index <= threadCount; index++) {
            Thread worker = new Thread(this::workerLoop, "Leafs Region Worker #" + index);
            worker.setDaemon(true);
            workers.add(worker);
        }
    }

    public void start() {
        workers.forEach(Thread::start);
    }

    public void shutdown() {
        running = false;
        workers.forEach(Thread::interrupt);
    }

    public void schedule(TickHandle handle) {
        handle.setScheduledStartNanos(System.nanoTime() + TICK_PERIOD_NANOS);
        queue.add(new ScheduledTick(handle));
    }

    public void runAttached(TickHandle handle) {
        executeTick(handle, 1);
    }

    static long computeTickCount(long idealStartNanos, long nowNanos) {
        return Math.max(1, 1 + (nowNanos - idealStartNanos) / TICK_PERIOD_NANOS);
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
            long now = System.nanoTime();
            long tickCount = computeTickCount(handle.scheduledStartNanos(), now);
            try {
                executeTick(handle, tickCount);
            } catch (Throwable throwable) {
                failurePolicy.accept(handle, throwable);
                continue;
            }

            if (!handle.isCancelled()) {
                long idealNext = handle.scheduledStartNanos() + tickCount * TICK_PERIOD_NANOS;
                handle.setScheduledStartNanos(Math.max(System.nanoTime(), idealNext));
                queue.add(next);
            }
        }
    }

    private void executeTick(TickHandle handle, long tickCount) {
        barrier.enterTick();
        RegionContext.enter(handle.context());
        watchdog.beginTick(handle);
        try {
            handle.tick(tickCount);
            handle.advance(tickCount);
        } catch (Throwable throwable) {
            crashWriter.write(handle.buildCrashReport(), throwable);
            throw throwable;
        } finally {
            watchdog.endTick(handle);
            RegionContext.exit();
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
