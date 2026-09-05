package fr.hardel.leafs.chunk.owner;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/** What a region runs at its next pass, in posting order. Closed once the region is gone, its remainder goes back through the owners. */
public final class RegionInbox {
    public record Posted(int chunkX, int chunkZ, Runnable task) {
    }

    private final ArrayDeque<Posted> tasks = new ArrayDeque<>();
    private boolean closed;

    /** False once closed: the region no longer exists, the caller resolves the owner again. */
    public synchronized boolean post(int chunkX, int chunkZ, Runnable task) {
        if (closed) {
            return false;
        }

        tasks.addLast(new Posted(chunkX, chunkZ, task));
        return true;
    }

    /** Everything posted before the call, so a task that re-posts to its own region waits for the next pass. */
    public int drain() {
        Posted[] batch;
        synchronized (this) {
            batch = tasks.toArray(Posted[]::new);
            tasks.clear();
        }

        for (Posted posted : batch) {
            posted.task().run();
        }

        return batch.length;
    }

    /** The end of the region: every posted task leaves through the consumer, nothing lands here again. */
    public void close(Consumer<Posted> leftover) {
        Posted[] batch;
        synchronized (this) {
            closed = true;
            batch = tasks.toArray(Posted[]::new);
            tasks.clear();
        }

        for (Posted posted : batch) {
            leftover.accept(posted);
        }
    }

    public synchronized int size() {
        return tasks.size();
    }
}
