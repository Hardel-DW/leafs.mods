package fr.hardel.leafs.ticking;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ThreadWaits {
    private static final ConcurrentHashMap<Thread, Wait> WAITING = new ConcurrentHashMap<>();

    public record Wait(long startedNanos, Supplier<String> what) {}

    private ThreadWaits() {}

    public static Wait open(Supplier<String> what) {
        return WAITING.put(Thread.currentThread(), new Wait(System.nanoTime(), what));
    }

    public static void close(Wait outer) {
        if (outer == null) {
            WAITING.remove(Thread.currentThread());
            return;
        }

        WAITING.put(Thread.currentThread(), outer);
    }

    public static String describe(Thread thread) {
        Wait wait = WAITING.get(thread);
        return wait == null ? null : wait.what().get();
    }

    public static Map<Thread, String> stalled(long nowNanos, long thresholdNanos) {
        Map<Thread, String> stalled = new HashMap<>();
        WAITING.forEach((thread, wait) -> {
            long waited = nowNanos - wait.startedNanos();
            if (waited >= thresholdNanos) {
                stalled.put(thread, "Wait stalled for " + waited / 1_000_000_000L + "s on thread '" + thread.getName() + "': " + wait.what().get());
            }
        });

        return stalled;
    }
}
