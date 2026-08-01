package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.Leafs;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Global-phase tasks, submittable from any thread — where off-thread {@code MinecraftServer.execute} lands. */
public final class GlobalScheduler {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public void run(Runnable task) {
        tasks.add(task);
    }

    public int drain() {
        int executed = 0;
        int budget = tasks.size();
        Runnable task;
        while (executed < budget && (task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable throwable) {
                Leafs.LOGGER.error("Global task failed", throwable);
            }
            executed++;
        }

        return executed;
    }
}
