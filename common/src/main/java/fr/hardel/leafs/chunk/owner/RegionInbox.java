package fr.hardel.leafs.chunk.owner;

import fr.hardel.leafs.Leafs;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RegionInbox {
    public record Posted(int chunkX, int chunkZ, Work work, Runnable task) {
    }

    private final ArrayDeque<Posted> chunkWork = new ArrayDeque<>();
    private final ArrayDeque<Posted> gameWork = new ArrayDeque<>();
    private final long slowTaskNanos;
    private final Predicate<Posted> owns;
    private final Consumer<Posted> elsewhere;
    private boolean closed;

    public RegionInbox(long slowTaskNanos) {
        this(slowTaskNanos, _ -> true, _ -> { });
    }

    public RegionInbox(long slowTaskNanos, Predicate<Posted> owns, Consumer<Posted> elsewhere) {
        this.slowTaskNanos = slowTaskNanos;
        this.owns = owns;
        this.elsewhere = elsewhere;
    }

    public synchronized boolean post(int chunkX, int chunkZ, Work work, Runnable task) {
        if (closed) {
            return false;
        }

        queueOf(work).addLast(new Posted(chunkX, chunkZ, work, task));
        return true;
    }

    public int drain() {
        return drain(Long.MAX_VALUE);
    }

    public int drain(long deadlineNanos) {
        return drain(chunkWork, deadlineNanos) + drain(gameWork, deadlineNanos);
    }

    public int drainChunkWork() {
        return drain(chunkWork, Long.MAX_VALUE);
    }

    private int drain(ArrayDeque<Posted> queue, long deadlineNanos) {
        int planned;
        synchronized (this) {
            planned = queue.size();
        }

        int ran = 0;
        while (ran < planned && System.nanoTime() < deadlineNanos) {
            Posted next;
            synchronized (this) {
                next = queue.pollFirst();
            }

            if (next == null) {
                break;
            }

            ran++;
            if (!owns.test(next)) {
                elsewhere.accept(next);
                continue;
            }

            long began = System.nanoTime();
            next.task().run();
            long took = System.nanoTime() - began;
            if (took >= slowTaskNanos) {
                String owner = next.task().getClass().getName();
                Leafs.LOGGER.warn("Inbox task at [{}, {}] took {} ms: {}", next.chunkX(), next.chunkZ(), took / 1_000_000L, owner.substring(owner.lastIndexOf('.') + 1));
            }
        }

        return ran;
    }

    public void close(Consumer<Posted> leftover) {
        Posted[] batch;
        synchronized (this) {
            closed = true;
            batch = new Posted[chunkWork.size() + gameWork.size()];
            int index = 0;
            for (Posted posted : chunkWork) {
                batch[index++] = posted;
            }

            for (Posted posted : gameWork) {
                batch[index++] = posted;
            }

            chunkWork.clear();
            gameWork.clear();
        }

        for (Posted posted : batch) {
            leftover.accept(posted);
        }
    }

    public synchronized int size() {
        return chunkWork.size() + gameWork.size();
    }

    private ArrayDeque<Posted> queueOf(Work work) {
        return work == Work.CHUNK ? chunkWork : gameWork;
    }
}
