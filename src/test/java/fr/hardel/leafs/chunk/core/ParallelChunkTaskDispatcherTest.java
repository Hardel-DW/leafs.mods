package fr.hardel.leafs.chunk.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The in-flight window of the dispatcher against a task that throws. Both executors run inline, so
 * one submit walks the whole dispatch chain before returning; the worker executor catches like the
 * chunk pool, whose threads hand an escaped throwable to their uncaught handler.
 */
class ParallelChunkTaskDispatcherTest {

    private static final int TICKET_LEVEL = 31;

    private final List<Throwable> reported = new ArrayList<>();
    private final Executor workers = task -> {
        try {
            task.run();
        } catch (Throwable failure) {
            reported.add(failure);
        }
    };

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private ParallelChunkTaskDispatcher dispatcher(int window) {
        return new ParallelChunkTaskDispatcher(TaskScheduler.wrapExecutor("test-workers", workers), Runnable::run, window);
    }

    private static void submit(ParallelChunkTaskDispatcher dispatcher, int chunkX, Runnable task) {
        dispatcher.submit(task, ChunkPos.pack(chunkX, 0), () -> TICKET_LEVEL);
    }

    @Test
    void aThrowingTaskFreesItsWindowSlot() {
        ParallelChunkTaskDispatcher dispatcher = dispatcher(1);
        RuntimeException failure = new RuntimeException("generation step blew up");
        List<String> ran = new ArrayList<>();

        submit(dispatcher, 0, () -> {
            throw failure;
        });
        submit(dispatcher, 1, () -> ran.add("after"));

        assertEquals(List.of("after"), ran, "the window must reopen once the failed task completed");
    }

    @Test
    void aThrowingTaskStillReachesThePool() {
        ParallelChunkTaskDispatcher dispatcher = dispatcher(1);
        RuntimeException failure = new RuntimeException("generation step blew up");

        submit(dispatcher, 0, () -> {
            throw failure;
        });

        assertEquals(1, reported.size(), "the failure must not be swallowed by the dispatcher");
        assertSame(failure, reported.getFirst());
    }
}
