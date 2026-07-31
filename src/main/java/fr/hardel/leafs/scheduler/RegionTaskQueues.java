package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongFunction;

/** A closed queue rejects offers, making the scheduler re-resolve the owner — how tasks survive merges and splits. */
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

    synchronized List<QueuedTask> drainSnapshot() {
        List<QueuedTask> drained = new ArrayList<>(tasks);
        tasks.clear();

        return drained;
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

    public void closeAndReroute(int sectionShift, LongFunction<RegionTaskQueues> queuesBySection) {
        synchronized (this) {
            closed = true;
            for (QueuedTask task : tasks) {
                long sectionKey = CoordinateKey.pack(task.chunkX() >> sectionShift, task.chunkZ() >> sectionShift);
                RegionTaskQueues target = queuesBySection.apply(sectionKey);
                if (target == null) {
                    throw new IllegalStateException("No child queue for section [" + CoordinateKey.x(sectionKey) + ", " + CoordinateKey.z(sectionKey) + "] while splitting");
                }

                synchronized (target) {
                    target.tasks.addLast(task);
                }
            }
            tasks.clear();
        }
    }

    synchronized int size() {
        return tasks.size();
    }
}
