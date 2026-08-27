package fr.hardel.leafs.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.List;

/** One mail queue per chunk, drained by whichever region owns the chunk at its tick start. Every post holds the chunk until its mail ran, so a holder and a region exist to drain it. */
public final class ChunkMailbox {
    private final ChunkHoldController holds;
    private final Long2ObjectOpenHashMap<ArrayDeque<Runnable>> mail = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap heldChunks = new Long2IntOpenHashMap();
    private volatile int pending;

    public ChunkMailbox(ChunkHoldController holds) {
        this.holds = holds;
    }

    public synchronized void post(int chunkX, int chunkZ, Runnable task) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        if (heldChunks.addTo(key, 1) == 0) {
            holds.addHold(chunkX, chunkZ);
        }

        mail.computeIfAbsent(key, _ -> new ArrayDeque<>()).addLast(task);
        pending++;
    }

    /** The owner's pass over its photo. Mail is popped one by one: a throw leaves the ones behind it queued, holds intact. */
    public int drain(List<ChunkHolder> holders) {
        if (pending == 0) {
            return 0;
        }

        int ran = 0;
        for (ChunkHolder holder : holders) {
            ran += drainChunk(holder.getPos().pack());
        }

        return ran;
    }

    /** Every chunk, for the thread that owns them all: the shutdown, or the server thread pumping while nothing else may run. */
    public int drainAll() {
        int ran = 0;
        for (long key : keys()) {
            ran += drainChunk(key);
        }

        return ran;
    }

    public int size() {
        return pending;
    }

    /** Bounded by the mail present at the start, so a task that re-posts to its own chunk waits for the next pass. */
    private int drainChunk(long key) {
        int budget = queued(key);
        int ran = 0;
        Runnable task;
        while (ran < budget && (task = poll(key)) != null) {
            ran++;
            try {
                task.run();
            } finally {
                release(key);
            }
        }

        return ran;
    }

    private synchronized int queued(long key) {
        ArrayDeque<Runnable> queue = mail.get(key);
        return queue == null ? 0 : queue.size();
    }

    private synchronized Runnable poll(long key) {
        ArrayDeque<Runnable> queue = mail.get(key);
        if (queue == null) {
            return null;
        }

        Runnable task = queue.pollFirst();
        if (queue.isEmpty()) {
            mail.remove(key);
        }

        return task;
    }

    private synchronized void release(long key) {
        pending--;
        if (heldChunks.addTo(key, -1) == 1) {
            heldChunks.remove(key);
            holds.removeHold(ChunkPos.getX(key), ChunkPos.getZ(key));
        }
    }

    private synchronized long[] keys() {
        return mail.keySet().toLongArray();
    }
}
