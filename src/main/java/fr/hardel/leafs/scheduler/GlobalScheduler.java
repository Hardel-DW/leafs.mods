package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.Leafs;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Global-phase tasks, submittable from any thread, where off-thread {@code MinecraftServer.execute} lands. */
public final class GlobalScheduler {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public void run(Runnable task) {
        tasks.add(task);
    }

    /** Runs what was queued before the call; true when anything ran. */
    public boolean drain() {
        int budget = tasks.size();
        boolean ran = false;
        Runnable task;
        while (budget-- > 0 && (task = tasks.poll()) != null) {
            ran = true;
            try {
                task.run();
            } catch (Throwable throwable) {
                Leafs.LOGGER.error("Global task failed", throwable);
            }
        }

        return ran;
    }
}
