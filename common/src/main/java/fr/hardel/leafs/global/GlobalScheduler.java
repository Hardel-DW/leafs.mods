package fr.hardel.leafs.global;

import fr.hardel.leafs.Leafs;
import net.minecraft.util.thread.BlockableEventLoop;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class GlobalScheduler {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final Consumer<Runnable> runner;
    private boolean draining;

    public GlobalScheduler(Consumer<Runnable> runner) {
        this.runner = runner;
    }

    public void run(Runnable task) {
        tasks.add(task);
    }

    public boolean drain() {
        if (draining) {
            return false;
        }

        draining = true;
        try {
            return runQueued();
        } finally {
            draining = false;
        }
    }

    private boolean runQueued() {
        int budget = tasks.size();
        boolean ran = false;
        Runnable task;
        while (budget-- > 0 && (task = tasks.poll()) != null) {
            ran = true;

            try {
                runner.accept(task);
            } catch (Exception exception) {
                Leafs.LOGGER.error("Global task failed", exception);
                if (BlockableEventLoop.isNonRecoverable(exception)) {
                    throw exception;
                }
            }
        }

        return ran;
    }
}
