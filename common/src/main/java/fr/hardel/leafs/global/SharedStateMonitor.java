package fr.hardel.leafs.global;

import java.util.function.Supplier;

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
