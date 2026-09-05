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

    /** As many tasks as were posted before the call, taken one at a time: a task that waits for a chunk drains the rest itself, and a re-post waits for the next pass. */
    public int drain() {
        int planned;
        synchronized (this) {
            planned = tasks.size();
        }

        int ran = 0;
        while (ran < planned) {
            Posted next;
            synchronized (this) {
                next = tasks.pollFirst();
            }

            if (next == null) {
                break;
            }

            next.task().run();
            ran++;
        }

        return ran;
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
