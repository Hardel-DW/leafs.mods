package fr.hardel.leafs.chunk.owner;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.pool.Urgency;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Who writes into the live world at a position: the thread that already owns it, the region covering it at its next tick, or the pool right now. */
public final class ChunkOwners {
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

    private final ChunkPool pool;
    private final int level;
    private final Inboxes inboxes;
    private final Ownership ownership;
    private final Urgency urgency;
    private final BooleanSupplier live;
    private final Executor serial;
    private final long slowTaskNanos;
    private final ConcurrentLong2ObjectMap<RegionInbox> borrowed = new ConcurrentLong2ObjectMap<>();
    private final ThreadLocal<Long> poolOwned = new ThreadLocal<>();

    /** Before the regions run and once they stopped, the server thread owns everything and its pump runs what other threads post. */
    public ChunkOwners(ChunkPool pool, int level, Inboxes inboxes, Ownership ownership, Urgency urgency, BooleanSupplier live, Executor serial, long slowTaskNanos) {
        this.pool = pool;
        this.level = level;
        this.inboxes = inboxes;
        this.ownership = ownership;
        this.urgency = urgency;
        this.live = live;
        this.serial = serial;
        this.slowTaskNanos = slowTaskNanos;
    }

    /** True when the task ran in line, which is what lets a caller read back what it wrote. Under a drain the owner posts to itself instead. */
    public boolean submit(int chunkX, int chunkZ, Runnable task) {
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
            if (inbox == null) {
                pool.submit(ChunkTask.of(ChunkPool.FIRST, area(chunkX, chunkZ, 0), () -> owning(chunkX, chunkZ, task)));
                return false;
            }

            if (inbox.post(chunkX, chunkZ, task)) {
                return false;
            }
        }
    }

    /** Pool work under the reservation of the area around a chunk, placed at the chunk. */
    public void onPool(int chunkX, int chunkZ, int radius, Runnable task) {
        pool.submit(ChunkTask.of(place(chunkX, chunkZ, chunkX, chunkZ), area(chunkX, chunkZ, radius), task));
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

    public Executor executor(int chunkX, int chunkZ) {
        return task -> submit(chunkX, chunkZ, task);
    }

    /** The keys of the square around a chunk; a negative radius reserves nothing. */
    public long[] area(int chunkX, int chunkZ, int radius) {
        if (radius < 0) {
            return NO_RESERVATION;
        }

        int side = 2 * radius + 1;
        long[] keys = new long[side * side];
        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                keys[count++] = ChunkTask.key(level, chunkX + dx, chunkZ + dz);
            }
        }

        return keys;
    }

    /** A head execution on the server thread takes a chunk no region covers: what lands there runs on the borrower until it releases. */
    public RegionInbox borrow(int chunkX, int chunkZ) {
        RegionInbox inbox = new RegionInbox(slowTaskNanos);
        borrowed.put(ChunkPos.pack(chunkX, chunkZ), inbox);
        return inbox;
    }

    public void release(int chunkX, int chunkZ, RegionInbox inbox) {
        borrowed.remove(ChunkPos.pack(chunkX, chunkZ), inbox);
        resubmit(inbox);
    }

    /** A dead region's inbox, handed back off the regionizer's lock. */
    public void abandon(RegionInbox inbox) {
        pool.execute(() -> resubmit(inbox));
    }

    /** A borrow that ended hands its inbox back: each task finds its owner again. */
    void resubmit(RegionInbox inbox) {
        inbox.close(posted -> submit(posted.chunkX(), posted.chunkZ(), posted.task()));
    }

    private void owning(int chunkX, int chunkZ, Runnable task) {
        Long previous = poolOwned.get();
        poolOwned.set(ChunkPos.pack(chunkX, chunkZ));
        try {
            task.run();
        } finally {
            poolOwned.set(previous);
        }
    }

    private @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        RegionInbox region = inboxes.at(chunkX, chunkZ);
        return region != null ? region : borrowed.get(ChunkPos.pack(chunkX, chunkZ));
    }
}
