package fr.hardel.leafs.chunk.core;

import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.TaskScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Vanilla pops one chunk and waits for it; this keeps popping until the in-flight window fills, priorities still apply at each pop. */
public final class ParallelChunkTaskDispatcher extends ChunkTaskDispatcher {
    private final TaskScheduler<Runnable> workers;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final int window;

    public ParallelChunkTaskDispatcher(TaskScheduler<Runnable> workers, Executor dispatcherExecutor, int window) {
        super(workers, dispatcherExecutor);
        this.workers = workers;
        this.window = window;
    }

    public int inFlight() {
        return inFlight.get();
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
        })).toArray(CompletableFuture[]::new)).whenComplete((_, _) -> {
            inFlight.decrementAndGet();
            pollTask();
        });
        pollTask();
    }
}
