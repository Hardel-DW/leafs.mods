package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.TickBarrier;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The once-per-global-tick window where single-threaded work runs with full world access (command
 * blocks now; functions and Fabric tick events join at M11). Empty queue = the barrier is never
 * raised and regions never pause — the no-op path that keeps scaling intact. A throwing task
 * propagates (vanilla crash semantics) but the barrier always drops.
 */
public final class BarrierWindow {
    private final TickBarrier barrier;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public BarrierWindow(TickBarrier barrier) {
        this.barrier = barrier;
    }

    public void enqueue(Runnable task) {
        tasks.add(task);
    }

    /** Called by the global phase after the level ticks; tasks queued during the drain wait for the next window. */
    public void runGlobalPhase() {
        if (tasks.isEmpty()) {
            return;
        }

        barrier.raise();
        try {
            int budget = tasks.size();
            Runnable task;
            while (budget-- > 0 && (task = tasks.poll()) != null) {
                task.run();
            }
        } finally {
            barrier.drop();
        }
    }

    public int pendingCount() {
        return tasks.size();
    }
}
