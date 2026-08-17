package fr.hardel.leafs.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exactly one of the two callbacks always runs. Retirement can come from another thread mid-tick,
 * so the run loop re-checks retirement before every task.
 */
public final class EntityScheduler<E> {
    private final List<ScheduledTask<E>> scheduled = new ArrayList<>();
    private final ArrayDeque<ScheduledTask<E>> due = new ArrayDeque<>();
    private long currentTick;
    private boolean retired;

    public synchronized boolean schedule(long delayTicks, Consumer<E> task, Runnable retiredCallback) {
        if (retired) {
            return false;
        }

        scheduled.add(new ScheduledTask<>(currentTick + Math.max(1, delayTicks), task, retiredCallback));
        return true;
    }

    public void tick(E entity) {
        synchronized (this) {
            if (retired) {
                return;
            }

            currentTick++;
            for (Iterator<ScheduledTask<E>> iterator = scheduled.iterator(); iterator.hasNext(); ) {
                ScheduledTask<E> task = iterator.next();
                if (task.dueTick() <= currentTick) {
                    due.addLast(task);
                    iterator.remove();
                }
            }
        }

        while (true) {
            ScheduledTask<E> task;
            synchronized (this) {
                if (retired) {
                    return;
                }

                task = due.pollFirst();
            }

            if (task == null) {
                return;
            }

            task.action().accept(entity);
        }
    }

    public void retire() {
        List<ScheduledTask<E>> pending;
        synchronized (this) {
            if (retired) {
                throw new IllegalStateException("Entity scheduler retired twice");
            }

            retired = true;
            pending = new ArrayList<>(due);
            pending.addAll(scheduled);
            due.clear();
            scheduled.clear();
        }

        for (ScheduledTask<E> task : pending) {
            if (task.retiredCallback() != null) {
                task.retiredCallback().run();
            }
        }
    }

    public synchronized boolean hasPendingTasks() {
        return !scheduled.isEmpty() || !due.isEmpty();
    }

    private record ScheduledTask<E>(long dueTick, Consumer<E> action, Runnable retiredCallback) {
    }
}
