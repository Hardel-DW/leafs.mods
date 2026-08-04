package fr.hardel.leafs.global;

import java.util.function.Supplier;

/**
 * The #23/#26 lock family: server-global state (scoreboard, saved data, maps, sequences, waypoints)
 * is mutated from region workers and the global phase alike; each instance serializes on itself.
 */
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
