package fr.hardel.leafs.chunk.pool;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Chunks held by running tasks. A blocked task parks behind the holder and is requeued when it frees, nobody waits. */
final class Reservations {
    private final ConcurrentHashMap<Long, Waiters> held = new ConcurrentHashMap<>();
    private final Consumer<ChunkTask> requeue;

    private static final class Waiters extends ArrayList<ChunkTask> {
        private boolean freed;
    }

    Reservations(Consumer<ChunkTask> requeue) {
        this.requeue = requeue;
    }

    boolean acquire(ChunkTask task) {
        long[] keys = task.reserved();
        retry:
        while (true) {
            Waiters mine = new Waiters();
            for (int index = 0; index < keys.length; index++) {
                Waiters present = held.putIfAbsent(keys[index], mine);
                if (present == null) {
                    continue;
                }

                for (int taken = 0; taken < index; taken++) {
                    held.remove(keys[taken], mine);
                }

                free(mine);
                synchronized (present) {
                    if (present.freed) {
                        continue retry;
                    }

                    present.add(task);
                }

                return false;
            }

            return true;
        }
    }

    void release(ChunkTask task) {
        Waiters holder = null;
        for (long key : task.reserved()) {
            holder = held.remove(key);
        }

        if (holder != null) {
            free(holder);
        }
    }

    private void free(Waiters waiters) {
        synchronized (waiters) {
            waiters.freed = true;
            waiters.forEach(requeue);
            waiters.clear();
        }
    }
}
