package fr.hardel.leafs.chunk.core;

import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.TaskScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The worldgen dispatcher pumped in continuous flow: vanilla pops one chunk's tasks and waits for
 * their completion before popping the next, which serializes generation per dimension. This subclass
 * keeps popping until the in-flight window fills, so the pool's workers all draw work, and priorities
 * still apply at every pop through the vanilla queue.
 */
public final class ParallelChunkTaskDispatcher extends ChunkTaskDispatcher {
    private final TaskScheduler<Runnable> workers;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final int window;

    public ParallelChunkTaskDispatcher(TaskScheduler<Runnable> workers, Executor dispatcherExecutor, int window) {
        super(workers, dispatcherExecutor);
        this.workers = workers;
        this.window = window;
    }

    @Override
    protected ChunkTaskPriorityQueue.TasksForChunk popTasks() {
        return inFlight.get() >= window ? null : super.popTasks();
    }

    @Override
    protected void scheduleForExecution(ChunkTaskPriorityQueue.TasksForChunk tasksForChunk) {
        inFlight.incrementAndGet();
        CompletableFuture.allOf(tasksForChunk.tasks().stream().map(task -> workers.<Unit>scheduleWithResult(future -> {
            try {
                task.run();
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
                throw failure;
            }

            future.complete(Unit.INSTANCE);
        })).toArray(CompletableFuture[]::new)).whenComplete((result, failure) -> {
            inFlight.decrementAndGet();
            pollTask();
        });
        pollTask();
    }
}
