package fr.hardel.leafs.chunk;

import fr.hardel.leafs.region.Region;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;

/** One mail queue per chunk, drained by the chunk's owner: its region at tick start, a chunk worker for a chunk no region owns. A chunk is drained by one thread at a time, the claim says which. Every post holds what its mail needs until it ran. */
public final class ChunkMailbox {
    private static final long CLAIM_WAIT_NANOS = 10_000L;

    private final ChunkHoldController holds;
    private final Executor workers;
    private final Long2ObjectOpenHashMap<ArrayDeque<Mail>> mail = new Long2ObjectOpenHashMap<>();
    private final EnumMap<MailHold.Level, Long2IntOpenHashMap> heldChunks = new EnumMap<>(MailHold.Level.class);
    private final ConcurrentHashMap<Long, Thread> claims = new ConcurrentHashMap<>();
    private volatile int pending;

    private record Mail(int chunkX, int chunkZ, MailHold hold, Runnable task) {}

    public ChunkMailbox(ChunkHoldController holds, Executor workers) {
        this.holds = holds;
        this.workers = workers;
        for (MailHold.Level level : MailHold.Level.values()) {
            heldChunks.put(level, new Long2IntOpenHashMap());
        }
    }

    public void post(int chunkX, int chunkZ, Runnable task) {
        post(chunkX, chunkZ, MailHold.CHUNK, task);
    }

    public synchronized void post(int chunkX, int chunkZ, MailHold hold, Runnable task) {
        Mail posted = new Mail(chunkX, chunkZ, hold, task);
        forEachHeld(posted, (x, z) -> {
            if (heldChunks.get(hold.level()).addTo(ChunkPos.pack(x, z), 1) == 0) {
                holds.addHold(x, z, hold.level());
            }
        });

        mail.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new ArrayDeque<>()).addLast(posted);
        pending++;
    }

    /** The owner's pass over its photo. Mail is popped one by one: a throw leaves the ones behind it queued, holds intact. */
    public int drain(List<ChunkHolder> holders) {
        if (pending == 0) {
            return 0;
        }

        int ran = 0;
        for (ChunkHolder holder : holders) {
            ran += drain(holder.getPos().pack());
        }

        return ran;
    }

    /** A borrowed region's chunks, for the thread that holds it. */
    public int drain(Region<?> region, ChunkMap chunkMap) {
        if (pending == 0) {
            return 0;
        }

        LongList keys = new LongArrayList();
        region.forEachChunk((chunkX, chunkZ) -> keys.add(ChunkPos.pack(chunkX, chunkZ)));
        int ran = 0;
        for (long key : keys) {
            if (chunkMap.getVisibleChunkIfPresent(key) != null) {
                ran += drain(key);
            }
        }

        return ran;
    }

    /** Every chunk, for the thread that owns them all: the shutdown, or the server thread pumping while nothing else may run. */
    public int drainAll() {
        int ran = 0;
        for (long key : keys()) {
            ran += drain(key);
        }

        return ran;
    }

    /** A chunk no region owns: a worker drains it when free, and comes back for what arrived meanwhile; a claim held elsewhere hands it over at release. */
    public void drainOnWorkers(long key) {
        workers.execute(() -> {
            if (!tryClaim(key)) {
                return;
            }

            try {
                drainChunk(key);
            } finally {
                releaseToWorkers(key);
            }
        });
    }

    /** The release of a chunk no region owns: the mail that arrived under the claim goes to a worker. */
    public void releaseToWorkers(long key) {
        release(key);
        if (queued(key) > 0) {
            drainOnWorkers(key);
        }
    }

    /** The chunk drained by this thread alone until {@link #release}; waits for the drain in flight, reentrant for the claimer. False when already claimed by this thread. */
    public boolean claim(long key) {
        Thread me = Thread.currentThread();
        while (true) {
            Thread holder = claims.putIfAbsent(key, me);
            if (holder == null) {
                return true;
            }

            if (holder == me) {
                return false;
            }

            LockSupport.parkNanos(CLAIM_WAIT_NANOS);
        }
    }

    public boolean tryClaim(long key) {
        return claims.putIfAbsent(key, Thread.currentThread()) == null;
    }

    /** The claimer owns the chunk: what it posts to that chunk runs in line. */
    public boolean claimedByCurrentThread(long key) {
        return claims.get(key) == Thread.currentThread();
    }

    public void release(long key) {
        claims.remove(key, Thread.currentThread());
    }

    public int size() {
        return pending;
    }

    /** One chunk, claimed for the pass unless this thread already holds it. */
    public int drain(long key) {
        boolean claimed = claim(key);
        try {
            return drainChunk(key);
        } finally {
            if (claimed) {
                release(key);
            }
        }
    }

    /** Bounded by the mail present at the start, so a task that re-posts to its own chunk waits for the next pass. */
    private int drainChunk(long key) {
        int budget = queued(key);
        int ran = 0;
        Mail next;
        while (ran < budget && (next = poll(key)) != null) {
            ran++;
            try {
                next.task().run();
            } finally {
                releaseHolds(next);
            }
        }

        return ran;
    }

    private synchronized int queued(long key) {
        ArrayDeque<Mail> queue = mail.get(key);
        return queue == null ? 0 : queue.size();
    }

    private synchronized Mail poll(long key) {
        ArrayDeque<Mail> queue = mail.get(key);
        if (queue == null) {
            return null;
        }

        Mail next = queue.pollFirst();
        if (queue.isEmpty()) {
            mail.remove(key);
        }

        return next;
    }

    private synchronized void releaseHolds(Mail ran) {
        pending--;
        Long2IntOpenHashMap held = heldChunks.get(ran.hold().level());
        forEachHeld(ran, (x, z) -> {
            long key = ChunkPos.pack(x, z);
            if (held.addTo(key, -1) == 1) {
                held.remove(key);
                holds.removeHold(x, z, ran.hold().level());
            }
        });
    }

    private static void forEachHeld(Mail mail, Region.ChunkConsumer consumer) {
        int radius = mail.hold().radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                consumer.accept(mail.chunkX() + dx, mail.chunkZ() + dz);
            }
        }
    }

    private synchronized long[] keys() {
        return mail.keySet().toLongArray();
    }
}
