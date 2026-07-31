package fr.hardel.leafs.scheduler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-entity task queue, ticked by the owning region. Tasks receive the CURRENT entity instance
 * (teleports recreate the object); once retired, pending tasks fire their retired callback instead.
 */
public final class EntityScheduler<E> {
    private final List<ScheduledTask<E>> tasks = new ArrayList<>();
    private long currentTick;
    private boolean retired;

    public synchronized boolean schedule(long delayTicks, Consumer<E> task, Runnable retiredCallback) {
        if (retired) {
            return false;
        }

        tasks.add(new ScheduledTask<>(currentTick + Math.max(1, delayTicks), task, retiredCallback));
        return true;
    }

    public void tick(E entity) {
        List<ScheduledTask<E>> due = new ArrayList<>();
        synchronized (this) {
            if (retired) {
                throw new IllegalStateException("Ticked a retired entity scheduler");
            }

            currentTick++;
            for (Iterator<ScheduledTask<E>> iterator = tasks.iterator(); iterator.hasNext(); ) {
                ScheduledTask<E> task = iterator.next();
                if (task.dueTick() <= currentTick) {
                    due.add(task);
                    iterator.remove();
                }
            }
        }

        for (ScheduledTask<E> task : due) {
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
            pending = new ArrayList<>(tasks);
            tasks.clear();
        }

        for (ScheduledTask<E> task : pending) {
            if (task.retiredCallback() != null) {
                task.retiredCallback().run();
            }
        }
    }

    public synchronized boolean isRetired() {
        return retired;
    }

    private record ScheduledTask<E>(long dueTick, Consumer<E> action, Runnable retiredCallback) {
    }
}
