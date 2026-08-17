package fr.hardel.leafs.global;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ticking.TickBarrier;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Once-per-global-tick window: single-threaded work with full world access. An empty queue never raises the barrier. */
public final class BarrierWindow {
    private final TickBarrier barrier;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private volatile Thread drainingThread;

    public BarrierWindow(TickBarrier barrier) {
        this.barrier = barrier;
    }

    public static BarrierWindow of(MinecraftServer server) {
        return ((GlobalServerAccess) server).leafs$barrierWindow();
    }

    public void enqueue(Runnable task) {
        tasks.add(task);
    }

    /** True while the calling thread runs a window task: its work already has the window's guarantees. */
    public boolean isDraining() {
        return drainingThread == Thread.currentThread();
    }

    /** Called by the global phase after the level ticks; tasks queued during the drain wait for the next window. */
    public void runGlobalPhase() {
        if (tasks.isEmpty()) {
            return;
        }

        barrier.raise();
        Thread outerDrain = drainingThread;
        drainingThread = Thread.currentThread();
        try {
            int budget = tasks.size();
            Runnable task;
            while (budget-- > 0 && (task = tasks.poll()) != null) {
                task.run();
            }
        } finally {
            drainingThread = outerDrain;
            barrier.drop();
        }
    }

    /**
     * Run before the worlds are saved so nothing queued by the final tick is lost. A failure here is
     * caught rather than propagated, since aborting {@code stopServer} would take the world save down with it.
     */
    public void runShutdownPhase() {
        try {
            runGlobalPhase();
        } catch (Throwable throwable) {
            Leafs.LOGGER.error("A barrier-window task failed during shutdown; the remaining ones are dropped", throwable);
        }

        int stranded = pendingCount();
        if (stranded > 0) {
            Leafs.LOGGER.warn("Dropping {} barrier-window task(s) left by the shutdown window", stranded);
            tasks.clear();
        }
    }

    public int pendingCount() {
        return tasks.size();
    }
}
