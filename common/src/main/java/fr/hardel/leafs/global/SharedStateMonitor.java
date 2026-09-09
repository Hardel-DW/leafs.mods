package fr.hardel.leafs.global;

import java.util.function.Supplier;

/** Serializes server-global state (scoreboard, saved data, maps, sequences) across region workers and the global phase. */
public final class SharedStateMonitor {

    private SharedStateMonitor() {
    }

    public static void run(Object monitor, Runnable action) {
        synchronized (monitor) {
            action.run();
        }
    }

    public static <T> T call(Object monitor, Supplier<T> action) {
        synchronized (monitor) {
            return action.get();
        }
    }
}
