package fr.hardel.leafs.chunk;

import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.ServerChunkCache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

/**
 * The level's dedicated chunk-system thread: sole owner of the chunk bookkeeping after the executor
 * identity re-point. It pumps the main-thread processor (distance updates, light scheduling, queued
 * tasks) and never waits on a region or the server thread — regions may block on it, never the
 * reverse. Game threads reach chunk state through {@link #runBlocking}.
 */
public final class ChunkSystemThread {
    private final ServerChunkCache cache;
    private final Thread thread;
    private volatile boolean running = true;

    public ChunkSystemThread(ServerChunkCache cache, String dimension) {
        this.cache = cache;
        this.thread = new Thread(() -> pump(dimension), "Leafs Chunk Thread (" + dimension + ")");
        this.thread.setDaemon(true);
    }

    public Thread start() {
        thread.start();

        return thread;
    }

    public void stop() {
        running = false;
        LockSupport.unpark(thread);
    }

    public boolean isCurrentThread() {
        return Thread.currentThread() == thread;
    }

    /** Vanilla ordering within a game tick depends on completion, so callers wait, never fire-and-forget. */
    public void runBlocking(Runnable task) {
        if (isCurrentThread()) {
            task.run();
            return;
        }

        CompletableFuture.runAsync(task, cache.mainThreadProcessor).join();
    }

    private void pump(String dimension) {
        RegionContext.enter(new RegionContext.Chunk(dimension));
        while (running) {
            if (!cache.pollTask()) {
                LockSupport.parkNanos(100_000L);
            }
        }
    }
}
