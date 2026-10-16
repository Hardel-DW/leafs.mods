package fr.hardel.leafs.thread;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** The events of the chunk lifecycle reach mods one at a time, as on the single thread of vanilla: mods keep indexes of loaded chunks in their handlers. */
public final class SerialEvents {
    private static final ReentrantLock DELIVERY = new ReentrantLock();

    private SerialEvents() {
    }

    public static <T> T deliver(Supplier<T> event) {
        DELIVERY.lock();
        try {
            return event.get();
        } finally {
            DELIVERY.unlock();
        }
    }

    /** A handler waiting on Leafs gives the delivery back: the chunk it waits for delivers its own events, as a nested load does on vanilla. */
    public static void released(Runnable wait) {
        int held = DELIVERY.getHoldCount();
        for (int hold = 0; hold < held; hold++) {
            DELIVERY.unlock();
        }

        try {
            wait.run();
        } finally {
            for (int hold = 0; hold < held; hold++) {
                DELIVERY.lock();
            }
        }
    }
}
