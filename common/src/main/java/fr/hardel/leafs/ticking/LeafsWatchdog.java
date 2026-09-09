package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.holder.ChunkWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/** Per-tick-unit watchdog, replaces vanilla's. Warn logs the stuck stack, kill (vanilla's max-tick-time, zero disables) runs once; the same killer covers a shutdown that never finishes. */
public final class LeafsWatchdog {
    /** Generous next to the kill threshold: a legitimate final save of a large world must never be cut short. */
    public static final Duration SHUTDOWN_DEADLINE = Duration.ofMinutes(5);

    private final long warnNanos;
    private final LongSupplier killNanos;
    private final LongFunction<Map<Thread, String>> stalledWaits;
    private final Consumer<String> reporter;
    private final Consumer<Stall> killer;
    private final ConcurrentHashMap<TickHandle, RunningTick> running = new ConcurrentHashMap<>();
    private final Map<Thread, Long> reportedWaits = new HashMap<>();
    private final AtomicReference<Thread> shutdownDeadline = new AtomicReference<>();
    private volatile boolean active;
    private Thread thread;

    /** A tick unit or a shutdown past the kill threshold; the thread is the stuck one, for the dump. */
    public record Stall(String summary, Thread thread) {
    }

    /** The stalled waits are the chunk waits older than the warn threshold at a given time, on any thread. */
    public LeafsWatchdog(Duration warnAfter, LongSupplier killNanos, LongFunction<Map<Thread, String>> stalledWaits, Consumer<String> reporter, Consumer<Stall> killer) {
        this.warnNanos = warnAfter.toNanos();
        this.killNanos = killNanos;
        this.stalledWaits = stalledWaits;
        this.reporter = reporter;
        this.killer = killer;
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

    /** Armed at stopServer: a JVM alive past the deadline gets the stuck-tick dump and kill. No-op when kill is disabled. */
    public void armShutdownDeadline(Duration deadline) {
        if (killNanos.getAsLong() == 0) {
            return;
        }

        Thread stopping = Thread.currentThread();
        Thread deadlineThread = new Thread(() -> awaitShutdown(deadline, stopping), "Leafs Shutdown Deadline");
        deadlineThread.setDaemon(true);
        if (shutdownDeadline.compareAndSet(null, deadlineThread)) {
            deadlineThread.start();
        }
    }

    /** In solo the JVM legitimately outlives the server, so a completed shutdown stands the deadline down. */
    public void disarmShutdownDeadline() {
        Thread deadlineThread = shutdownDeadline.get();
        if (deadlineThread != null) {
            deadlineThread.interrupt();
        }
    }

    void beginTick(TickHandle handle) {
        running.put(handle, new RunningTick(Thread.currentThread(), System.nanoTime()));
    }

    void endTick(TickHandle handle) {
        running.remove(handle);
    }

    private void watch() {
        long checkMillis = Math.clamp(warnNanos / 4_000_000L, 10L, 1_000L);
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
                if (killNanos.getAsLong() > 0 && now - tick.startNanos >= killNanos.getAsLong() && !tick.killed) {
                    tick.killed = true;
                    killer.accept(new Stall(headerLine(entry.getKey(), tick, now), tick.thread));
                } else if (now - Math.max(tick.startNanos, tick.lastReportNanos) >= warnNanos) {
                    tick.lastReportNanos = now;
                    reporter.accept(describeStall(entry.getKey(), tick, now));
                }
            }

            reportStalledWaits(now);
        }
    }

    /** A chunk wait past the threshold on a thread that is not a tick unit, a chunk worker or the server thread: at once, then once per warn interval. */
    private void reportStalledWaits(long now) {
        Map<Thread, String> stalled = stalledWaits.apply(now);
        reportedWaits.keySet().retainAll(stalled.keySet());
        for (RunningTick tick : running.values()) {
            stalled.remove(tick.thread);
        }

        stalled.forEach((thread, summary) -> {
            Long lastReport = reportedWaits.get(thread);
            if (lastReport == null || now - lastReport >= warnNanos) {
                reportedWaits.put(thread, now);
                reporter.accept(withStack(new StringBuilder(summary), thread));
            }
        });
    }


    private void awaitShutdown(Duration deadline, Thread stopping) {
        try {
            Thread.sleep(deadline);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }

        killer.accept(new Stall("Server shutdown still not finished after " + deadline.toSeconds() + "s, stopping thread '" + stopping.getName() + "'", stopping));
    }

    private String headerLine(TickHandle handle, RunningTick tick, long now) {
        return "Region tick stalled for " + (now - tick.startNanos) / 1_000_000_000L + "s: region #" + handle.id() + " in " + handle.dimension() + ", tick " + handle.currentTick() + ", thread '" + tick.thread.getName() + "'";
    }

    private String describeStall(TickHandle handle, RunningTick tick, long now) {
        StringBuilder message = new StringBuilder(headerLine(handle, tick, now));
        String waiting = ChunkWait.describe(tick.thread);
        if (waiting != null) {
            message.append(System.lineSeparator()).append('	').append(waiting);
        }

        return withStack(message, tick.thread);
    }

    private static String withStack(StringBuilder message, Thread thread) {
        for (StackTraceElement element : thread.getStackTrace()) {
            message.append(System.lineSeparator()).append("\tat ").append(element);
        }

        return message.toString();
    }

    private static final class RunningTick {
        final Thread thread;
        final long startNanos;
        volatile long lastReportNanos;
        volatile boolean killed;

        RunningTick(Thread thread, long startNanos) {
            this.thread = thread;
            this.startNanos = startNanos;
        }
    }
}
