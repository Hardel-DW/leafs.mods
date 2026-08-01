package fr.hardel.leafs.global;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ticking.TickBarrier;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The once-per-global-tick window where single-threaded work runs with full world access (command
 * blocks now; functions and Fabric tick events join at M11). Empty queue = the barrier is never
 * raised and regions never pause — the no-op path that keeps scaling intact.
 *
 * <p>One drain consumes the tasks queued before it and no more, so a task that re-queues itself
 * cannot hold the barrier up forever; it lands in the next window instead. A throwing task
 * propagates (vanilla crash semantics) and the barrier still drops, on that path and on the path
 * where raising it fails.
 */
public final class BarrierWindow {
    private final TickBarrier barrier;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private volatile Thread drainingThread;

    public BarrierWindow(TickBarrier barrier) {
        this.barrier = barrier;
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
     * The last window, run before the worlds are saved so nothing queued by the final tick is lost.
     * Nothing escapes it: a failure here would abort {@code stopServer} and take the world save with
     * it, which is worse than any command left unrun. Whatever the drain queues in turn is dropped —
     * the server is closing.
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
