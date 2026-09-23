package fr.hardel.leafs.ticking;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

public final class LeafsWatchdog {
    private final long warnNanos;
    private final LongSupplier killNanos;
    private final LongFunction<Map<Thread, String>> stalledWaits;
    private final Consumer<String> reporter;
    private final Consumer<Stall> killer;
    private final ConcurrentHashMap<TickHandle, RunningTick> running = new ConcurrentHashMap<>();
    private final Map<Thread, Long> reportedWaits = new HashMap<>();
    private final Thread thread = new Thread(this::watch, "Leafs Watchdog");

    public record Stall(String summary, Thread thread) {
    }

    public LeafsWatchdog(long warnNanos, LongSupplier killNanos, LongFunction<Map<Thread, String>> stalledWaits, Consumer<String> reporter, Consumer<Stall> killer) {
        this.warnNanos = warnNanos;
        this.killNanos = killNanos;
        this.stalledWaits = stalledWaits;
        this.reporter = reporter;
        this.killer = killer;
    }

    public void start() {
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        thread.interrupt();
    }

    void beginTick(TickHandle handle) {
        running.put(handle, new RunningTick(Thread.currentThread(), System.nanoTime()));
    }

    void endTick(TickHandle handle) {
        running.remove(handle);
    }

    private void watch() {
        long checkMillis = Math.clamp(warnNanos / 4_000_000L, 10L, 1_000L);
        while (true) {
            try {
                Thread.sleep(checkMillis);
            } catch (InterruptedException exception) {
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

    private String headerLine(TickHandle handle, RunningTick tick, long now) {
        return "Region tick stalled for %ss: region #%s in %s, tick %s, thread '%s'".formatted(
            (now - tick.startNanos) / 1_000_000_000L, handle.id(), handle.dimension(), handle.currentTick(), tick.thread.getName());
    }

    private String describeStall(TickHandle handle, RunningTick tick, long now) {
        StringBuilder message = new StringBuilder(headerLine(handle, tick, now));
        String waiting = ThreadWaits.describe(tick.thread);
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
