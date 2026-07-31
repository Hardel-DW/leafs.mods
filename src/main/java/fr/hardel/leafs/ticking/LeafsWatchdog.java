package fr.hardel.leafs.ticking;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Replaces the vanilla single-thread watchdog: per-tick-unit deadlines, stack dump of the stuck
 * thread only. Detection and reporting, never recovery.
 */
public final class LeafsWatchdog {
    private final long warnNanos;
    private final Consumer<String> reporter;
    private final ConcurrentHashMap<TickHandle, RunningTick> running = new ConcurrentHashMap<>();
    private volatile boolean active;
    private Thread thread;

    public LeafsWatchdog(Duration warnAfter, Consumer<String> reporter) {
        this.warnNanos = warnAfter.toNanos();
        this.reporter = reporter;
    }

    public void start() {
        active = true;
        thread = new Thread(this::watch, "Leafs Watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        active = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    void beginTick(TickHandle handle) {
        running.put(handle, new RunningTick(Thread.currentThread(), System.nanoTime()));
    }

    void endTick(TickHandle handle) {
        running.remove(handle);
    }

    private void watch() {
        long checkMillis = Math.max(10, warnNanos / 4_000_000);
        while (active) {
            try {
                Thread.sleep(checkMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            long now = System.nanoTime();
            for (var entry : running.entrySet()) {
                RunningTick tick = entry.getValue();
                if (now - Math.max(tick.startNanos, tick.lastReportNanos) >= warnNanos) {
                    tick.lastReportNanos = now;
                    reporter.accept(describeStall(entry.getKey(), tick, now));
                }
            }
        }
    }

    private String describeStall(TickHandle handle, RunningTick tick, long now) {
        StringBuilder message = new StringBuilder();
        message.append("Region tick stalled for ").append((now - tick.startNanos) / 1_000_000_000L).append("s: region #").append(handle.id()).append(" in ").append(handle.dimension()).append(", tick ").append(handle.currentTick()).append(", thread '").append(tick.thread.getName()).append("'");
        for (StackTraceElement element : tick.thread.getStackTrace()) {
            message.append(System.lineSeparator()).append("\tat ").append(element);
        }

        return message.toString();
    }

    private static final class RunningTick {
        final Thread thread;
        final long startNanos;
        volatile long lastReportNanos;

        RunningTick(Thread thread, long startNanos) {
            this.thread = thread;
            this.startNanos = startNanos;
        }
    }
}
