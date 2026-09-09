package fr.hardel.leafs.chunk.owner;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.pool.Urgency;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Who writes into the live world at a position: the thread that already owns it, the region covering it at its next tick, or without a region the pool for
 * chunk work and the calling thread for game work, which takes the chunk for the task and reads back what it writes, like vanilla. See {@link Work}.
 */
public final class ChunkOwners implements Router {
    private static final long[] NO_RESERVATION = {};

    /** The region's inbox at a chunk, null where no region covers it. */
    @FunctionalInterface
    public interface Inboxes {
        @Nullable RegionInbox at(int chunkX, int chunkZ);
    }

    /** Whether the current thread may write at a chunk now: its region ticking, a borrow holding it, or a universal owner. */
    @FunctionalInterface
    public interface Ownership {
        boolean holds(int chunkX, int chunkZ);
    }

    /** The calling thread takes a chunk no region covers for the task and runs it; false when another thread holds the chunk, the task is then mail for that thread. */
    @FunctionalInterface
    public interface Taker {
        boolean take(int chunkX, int chunkZ, Runnable task);
    }

    private final ChunkPool pool;
    private final int level;
    private final Inboxes inboxes;
    private final Ownership ownership;
    private final Urgency urgency;
    private final BooleanSupplier live;
    private final Executor serial;
    private final Taker taker;
    private final GlobalScheduler server;
    private final long slowTaskNanos;
    private final ConcurrentLong2ObjectMap<RegionInbox> borrowed = new ConcurrentLong2ObjectMap<>();
    private final ThreadLocal<Long> poolOwned = new ThreadLocal<>();

    /** Before the regions run and once they stopped, the server thread owns everything and its pump runs what other threads post. */
    public ChunkOwners(ChunkPool pool, int level, Inboxes inboxes, Ownership ownership, Urgency urgency, BooleanSupplier live, Executor serial, Taker taker, GlobalScheduler server, long slowTaskNanos) {
        this.pool = pool;
        this.level = level;
        this.inboxes = inboxes;
        this.ownership = ownership;
        this.urgency = urgency;
        this.live = live;
        this.serial = serial;
        this.taker = taker;
        this.server = server;
        this.slowTaskNanos = slowTaskNanos;
    }

    /** True when the task ran in line, which is what lets a caller read back what it wrote. Under a graph drain the owner posts to itself instead. */
    public boolean submit(int chunkX, int chunkZ, Work work, Runnable task) {
        if (holds(chunkX, chunkZ) && !ChunkLevels.draining()) {
            task.run();
            return true;
        }

        if (!live.getAsBoolean()) {
            serial.execute(task);
            return false;
        }

        while (true) {
            RegionInbox inbox = inboxAt(chunkX, chunkZ);
            if (inbox != null) {
                if (inbox.post(chunkX, chunkZ, work, task)) {
                    return false;
                }

                continue;
            }

            if (work == Work.CHUNK) {
                pool.submit(ChunkTask.of(ChunkTask.Kind.OWNER, ChunkPool.FIRST, area(ChunkTask.Kind.OWNER, chunkX, chunkZ, 0), () -> onPoolStart(chunkX, chunkZ, task)));
                return false;
            }

            // The pool never runs game work, it could wait for a chunk under its own reservation: the server thread routes it again.
            if (ChunkPool.isWorker()) {
                server.run(() -> submit(chunkX, chunkZ, work, task));
                return false;
            }

            // A chunk another thread holds is found in its inbox on the next turn of the loop.
            if (taker.take(chunkX, chunkZ, task)) {
                return true;
            }
        }
    }

    @Override
    public void route(int chunkX, int chunkZ, Runnable task) {
        submit(chunkX, chunkZ, Work.GAME, task);
    }

    public void later(int chunkX, int chunkZ, Work work, Runnable task) {
        RegionInbox inbox = inboxAt(chunkX, chunkZ);
        if (live.getAsBoolean() && inbox != null && inbox.post(chunkX, chunkZ, work, task)) {
            return;
        }

        server.run(() -> submit(chunkX, chunkZ, work, task));
    }

    /** Pool work under the reservation of the area around a chunk, placed at the chunk. */
    public void onPool(ChunkTask.Kind kind, int chunkX, int chunkZ, int radius, Runnable task) {
        pool.submit(ChunkTask.of(kind, place(chunkX, chunkZ, chunkX, chunkZ), area(kind, chunkX, chunkZ, radius), task));
    }

    public ChunkTask.Place place(int chunkX, int chunkZ, int centerX, int centerZ) {
        return new ChunkTask.Place(ChunkTask.key(level, chunkX, chunkZ), ChunkTask.key(level, centerX, centerZ), urgency);
    }

    /** Fed by the players graph: a chunk whose distance to the players changed re-prioritises what the pool queued on it. */
    public LevelListener follow() {
        return (chunkKey, _, _) -> pool.changed(ChunkTask.key(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
    }

    /** Everything queued within vanilla's radius of a required chunk heads the pool, disk reads and light included. */
    public void expedite(int chunkX, int chunkZ) {
        int radius = ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                pool.expedite(ChunkTask.key(level, chunkX + dx, chunkZ + dz));
            }
        }
    }

    public String describeQueued(int chunkX, int chunkZ) {
        int radius = ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int around = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                around += pool.queuedAt(ChunkTask.key(level, chunkX + dx, chunkZ + dz));
            }
        }

        return around + " tasks queued within " + radius + ", " + pool.queuedAt(ChunkTask.key(level, chunkX, chunkZ)) + " on the chunk itself";
    }

    /** A worker holds the chunk of the owner task it runs. */
    public boolean holds(int chunkX, int chunkZ) {
        Long owned = poolOwned.get();
        return owned != null && owned == ChunkPos.pack(chunkX, chunkZ) || ownership.holds(chunkX, chunkZ);
    }

    /** Vanilla's executor for the holder futures, promotions and teardowns: chunk work. */
    public Executor executor(int chunkX, int chunkZ) {
        return task -> submit(chunkX, chunkZ, Work.CHUNK, task);
    }

    public long[] area(ChunkTask.Kind kind, int chunkX, int chunkZ, int radius) {
        if (radius < 0) {
            return NO_RESERVATION;
        }

        int space = space(kind);
        int side = 2 * radius + 1;
        long[] keys = new long[side * side];
        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                keys[count++] = ChunkTask.key(space, chunkX + dx, chunkZ + dz);
            }
        }

        return keys;
    }

    private int space(ChunkTask.Kind kind) {
        return kind == ChunkTask.Kind.LIGHT ? level * 2 + 1 : level * 2;
    }

    /** A thread takes a chunk no region covers: what lands there waits in the inbox for the taker until it releases. Null when another thread holds it. */
    public @Nullable RegionInbox borrow(int chunkX, int chunkZ) {
        RegionInbox inbox = new RegionInbox(slowTaskNanos);
        return borrowed.putIfAbsent(ChunkPos.pack(chunkX, chunkZ), inbox) == null ? inbox : null;
    }

    public void release(int chunkX, int chunkZ, RegionInbox inbox) {
        borrowed.remove(ChunkPos.pack(chunkX, chunkZ), inbox);
        resubmit(inbox);
    }

    /** A dead region's inbox, handed back off the regionizer's lock. */
    public void abandon(RegionInbox inbox) {
        pool.execute(() -> resubmit(inbox));
    }

    /** A borrow that ended hands its inbox back: each task finds its owner again, as the work it is. */
    void resubmit(RegionInbox inbox) {
        inbox.close(posted -> submit(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task()));
    }

    /** The pool task reads the owner again when it starts: a region or a taker that arrived while it queued gets the task instead of racing it. Otherwise it takes the chunk like a taker, for the task, so a taker meanwhile finds it held. */
    private void onPoolStart(int chunkX, int chunkZ, Runnable task) {
        while (true) {
            RegionInbox inbox = inboxAt(chunkX, chunkZ);
            if (inbox != null) {
                if (inbox.post(chunkX, chunkZ, Work.CHUNK, task)) {
                    return;
                }

                continue;
            }

            RegionInbox claim = borrow(chunkX, chunkZ);
            if (claim != null) {
                owning(chunkX, chunkZ, claim, task);
                return;
            }
        }
    }

    /** What lands on the chunk meanwhile waits in the claim and finds its owner again at release. */
    private void owning(int chunkX, int chunkZ, RegionInbox claim, Runnable task) {
        Long previous = poolOwned.get();
        poolOwned.set(ChunkPos.pack(chunkX, chunkZ));
        try {
            task.run();
        } finally {
            poolOwned.set(previous);
            release(chunkX, chunkZ, claim);
        }
    }

    private @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        RegionInbox region = inboxes.at(chunkX, chunkZ);
        return region != null ? region : borrowed.get(ChunkPos.pack(chunkX, chunkZ));
    }
}
