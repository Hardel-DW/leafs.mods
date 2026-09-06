package fr.hardel.leafs.chunk.owner;

import fr.hardel.leafs.Leafs;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** What an owner runs at its next pass, chunk work and game work each in posting order; what it no longer owns leaves through the owners instead. Closed once the owner is gone, its remainder goes back the same way. */
public final class RegionInbox {
    public record Posted(int chunkX, int chunkZ, Work work, Runnable task) {
    }

    private final ArrayDeque<Posted> chunkWork = new ArrayDeque<>();
    private final ArrayDeque<Posted> gameWork = new ArrayDeque<>();
    private final long slowTaskNanos;
    private final Predicate<Posted> owns;
    private final Consumer<Posted> elsewhere;
    private boolean closed;

    /** The box of a chunk another thread took: every task in it is for that chunk. */
    public RegionInbox(long slowTaskNanos) {
        this(slowTaskNanos, _ -> true, _ -> { });
    }

    /** A task longer than the threshold is logged with its class and chunk, the one place where a publication can cost a tick. A task on a chunk the owner lost, a section reclaimed from a region, leaves through {@code elsewhere}. */
    public RegionInbox(long slowTaskNanos, Predicate<Posted> owns, Consumer<Posted> elsewhere) {
        this.slowTaskNanos = slowTaskNanos;
        this.owns = owns;
        this.elsewhere = elsewhere;
    }

    /** False once closed: the region no longer exists, the caller resolves the owner again. */
    public synchronized boolean post(int chunkX, int chunkZ, Work work, Runnable task) {
        if (closed) {
            return false;
        }

        queueOf(work).addLast(new Posted(chunkX, chunkZ, work, task));
        return true;
    }

    /** Vanilla's main-thread queue semantics for the pass: everything posted before the call, chunk work first. */
    public int drain() {
        return drain(Long.MAX_VALUE);
    }

    /** The same pass, stopped once the deadline is past; what is left waits in order for the next pass. */
    public int drain(long deadlineNanos) {
        return drain(chunkWork, deadlineNanos) + drain(gameWork, deadlineNanos);
    }

    /** What a wait may run: chunk work never waits, so a task waiting for a chunk reaches the publication behind it. Game work waits its turn, one task at a time, in order. */
    public int drainChunkWork() {
        return drain(chunkWork, Long.MAX_VALUE);
    }

    /** As many tasks as were posted before the call, taken one at a time: a re-post waits for the next pass. */
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

    /** The end of the region: every posted task leaves through the consumer, nothing lands here again. */
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
