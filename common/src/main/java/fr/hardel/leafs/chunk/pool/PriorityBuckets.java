package fr.hardel.leafs.chunk.pool;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** One queue per priority, 0 first. A moved task leaves a stale entry behind, which the poll skips. */
final class PriorityBuckets {
    private final List<ConcurrentLinkedQueue<ChunkTask>> buckets;

    PriorityBuckets(int count) {
        List<ConcurrentLinkedQueue<ChunkTask>> created = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            created.add(new ConcurrentLinkedQueue<>());
        }

        buckets = List.copyOf(created);
    }


    void add(ChunkTask task) {
        int bucket = clamp(task.priority());
        task.enqueuedAt(bucket);
        buckets.get(bucket).add(task);
    }

    boolean move(ChunkTask task, int priority) {
        int bucket = clamp(priority);
        if (!task.moveTo(bucket)) {
            return false;
        }

        buckets.get(bucket).add(task);
        return true;
    }

    @Nullable
    ChunkTask poll() {
        for (int bucket = 0; bucket < buckets.size(); bucket++) {
            ConcurrentLinkedQueue<ChunkTask> queue = buckets.get(bucket);
            ChunkTask task;
            while ((task = queue.poll()) != null) {
                if (task.claimAt(bucket)) {
                    return task;
                }
            }
        }

        return null;
    }

    private int clamp(int priority) {
        return Math.clamp(priority, 0, buckets.size() - 1);
    }
}
