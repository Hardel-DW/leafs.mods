package fr.hardel.leafs.chunk.pool;

import fr.hardel.excess.ConcurrentLong2ObjectMap;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

final class Reservations {
    private final ConcurrentLong2ObjectMap<ChunkTask> held = new ConcurrentLong2ObjectMap<>();
    private final Consumer<ChunkTask> requeue;

    Reservations(Consumer<ChunkTask> requeue) {
        this.requeue = requeue;
    }

    @Nullable ChunkTask acquire(ChunkTask task) {
        long[] keys = task.reserved();
        retry:
        while (true) {
            for (int index = 0; index < keys.length; index++) {
                ChunkTask present = held.putIfAbsent(keys[index], task);
                if (present == null) {
                    continue;
                }

                for (int taken = 0; taken < index; taken++) {
                    held.remove(keys[taken], task);
                }

                free(task);
                synchronized (present) {
                    if (held.get(keys[index]) != present) {
                        continue retry;
                    }

                    if (present.waiters == null) {
                        present.waiters = new ArrayList<>();
                    }

                    present.waiters.add(task);
                }

                return present;
            }

            return null;
        }
    }

    void release(ChunkTask task) {
        for (long key : task.reserved()) {
            held.remove(key);
        }

        free(task);
    }

    private void free(ChunkTask holder) {
        synchronized (holder) {
            if (holder.waiters != null) {
                holder.waiters.forEach(requeue);
                holder.waiters = null;
            }
        }
    }
}
