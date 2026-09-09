package fr.hardel.leafs.chunk.pool;

import fr.hardel.excess.ConcurrentLong2ObjectMap;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Chunks held by running tasks. A blocked task parks behind the holder and is requeued when it frees, nobody waits. */
final class Reservations {
    private final ConcurrentLong2ObjectMap<Waiters> held = new ConcurrentLong2ObjectMap<>();
    private final Consumer<ChunkTask> requeue;

    private static final class Waiters extends ArrayList<ChunkTask> {
        private final ChunkTask holder;
        private boolean freed;

        private Waiters(ChunkTask holder) {
            this.holder = holder;
        }
    }

    Reservations(Consumer<ChunkTask> requeue) {
        this.requeue = requeue;
    }

    /** Null once the task holds every chunk it reserves; otherwise the running task it parked behind. */
    @Nullable ChunkTask acquire(ChunkTask task) {
        long[] keys = task.reserved();
        retry:
        while (true) {
            Waiters mine = new Waiters(task);
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

                return present.holder;
            }

            return null;
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
