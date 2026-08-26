package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * A closed queue rejects offers, making the scheduler re-resolve the owner - how tasks survive merges
 * and splits. Closing only ever happens under the regionizer's write lock, which is also what orders
 * the two nested monitors below: no two closures of the same pair can run in opposite directions.
 */
public final class RegionTaskQueues {
    private final ArrayDeque<QueuedTask> tasks = new ArrayDeque<>();
    private boolean closed;

    synchronized boolean offer(QueuedTask task) {
        if (closed) {
            return false;
        }

        tasks.addLast(task);

        return true;
    }

    synchronized QueuedTask poll() {
        return tasks.pollFirst();
    }

    /** Folia merge order: the closing queue's tasks land before the target's existing ones. */
    public void closeInto(RegionTaskQueues target) {
        synchronized (this) {
            closed = true;
            synchronized (target) {
                for (Iterator<QueuedTask> iterator = tasks.descendingIterator(); iterator.hasNext(); ) {
                    target.tasks.addFirst(iterator.next());
                }
            }
            tasks.clear();
        }
    }

    /** A task whose section died with the split has no child; it goes to the caller's sink. */
    public void closeAndReroute(int sectionShift, LongFunction<RegionTaskQueues> queuesBySection, Consumer<Runnable> orphans) {
        synchronized (this) {
            closed = true;
            for (QueuedTask task : tasks) {
                long sectionKey = CoordinateKey.pack(task.chunkX() >> sectionShift, task.chunkZ() >> sectionShift);
                RegionTaskQueues target = queuesBySection.apply(sectionKey);
                if (target == null) {
                    orphans.accept(task.action());
                    continue;
                }

                synchronized (target) {
                    target.tasks.addLast(task);
                }
            }
            tasks.clear();
        }
    }

    /** Death without a merge target: the remaining tasks drain to the caller's sink, serially. */
    public void closeDraining(Consumer<Runnable> sink) {
        synchronized (this) {
            closed = true;
            for (QueuedTask task : tasks) {
                sink.accept(task.action());
            }
            tasks.clear();
        }
    }

    public synchronized int size() {
        return tasks.size();
    }
}
