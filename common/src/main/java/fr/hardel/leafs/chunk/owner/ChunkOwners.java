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

public final class ChunkOwners implements Router {
    private static final long[] NO_RESERVATION = {};

    @FunctionalInterface
    public interface Inboxes {
        @Nullable RegionInbox at(int chunkX, int chunkZ);
    }

    @FunctionalInterface
    public interface Ownership {
        boolean holds(int chunkX, int chunkZ);
    }

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
                onPool(chunkX, chunkZ, task);
                return false;
            }

            if (ChunkPool.isWorker()) {
                server.run(() -> submit(chunkX, chunkZ, work, task));
                return false;
            }

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
        if (!live.getAsBoolean()) {
            server.run(() -> submit(chunkX, chunkZ, work, task));
            return;
        }

        RegionInbox inbox = inboxAt(chunkX, chunkZ);
        if (inbox != null && inbox.post(chunkX, chunkZ, work, task)) {
            return;
        }

        if (work == Work.CHUNK) {
            onPool(chunkX, chunkZ, task);
            return;
        }

        server.run(() -> submit(chunkX, chunkZ, work, task));
    }

    private void onPool(int chunkX, int chunkZ, Runnable task) {
        pool.submit(ChunkTask.of(ChunkTask.Kind.OWNER, ChunkPool.FIRST, area(ChunkTask.Kind.OWNER, chunkX, chunkZ, 0), () -> onPoolStart(chunkX, chunkZ, task)));
    }

    public void onPool(ChunkTask.Kind kind, int chunkX, int chunkZ, int radius, Runnable task) {
        pool.submit(ChunkTask.of(kind, place(chunkX, chunkZ, chunkX, chunkZ), area(kind, chunkX, chunkZ, radius), task));
    }

    public ChunkTask.Place place(int chunkX, int chunkZ, int centerX, int centerZ) {
        return new ChunkTask.Place(ChunkTask.key(level, chunkX, chunkZ), ChunkTask.key(level, centerX, centerZ), urgency);
    }

    public LevelListener follow() {
        return (chunkKey, _, _) -> pool.changed(ChunkTask.key(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
    }

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

    public boolean holds(int chunkX, int chunkZ) {
        RegionInbox taken = borrowed.get(ChunkPos.pack(chunkX, chunkZ));
        return taken != null ? taken.heldBy(Thread.currentThread()) : ownership.holds(chunkX, chunkZ);
    }

    public boolean heldElsewhere(int chunkX, int chunkZ) {
        RegionInbox taken = borrowed.get(ChunkPos.pack(chunkX, chunkZ));
        return taken != null && !taken.heldBy(Thread.currentThread());
    }

    public @Nullable String describeTaken(int chunkX, int chunkZ) {
        RegionInbox taken = borrowed.get(ChunkPos.pack(chunkX, chunkZ));
        return taken == null ? null : "taken by thread '%s' with %d queued".formatted(taken.holderName(), taken.size());
    }

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

    public @Nullable RegionInbox borrow(int chunkX, int chunkZ) {
        RegionInbox inbox = new RegionInbox(slowTaskNanos);
        return borrowed.putIfAbsent(ChunkPos.pack(chunkX, chunkZ), inbox) == null ? inbox : null;
    }

    public void release(int chunkX, int chunkZ, RegionInbox inbox) {
        borrowed.remove(ChunkPos.pack(chunkX, chunkZ), inbox);
        resubmit(inbox);
    }

    public void abandon(RegionInbox inbox) {
        pool.execute(() -> resubmit(inbox));
    }

    void resubmit(RegionInbox inbox) {
        inbox.close(posted -> later(posted.chunkX(), posted.chunkZ(), posted.work(), posted.task()));
    }

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

    private void owning(int chunkX, int chunkZ, RegionInbox claim, Runnable task) {
        try {
            task.run();
        } finally {
            release(chunkX, chunkZ, claim);
        }
    }

    private @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        RegionInbox taken = borrowed.get(ChunkPos.pack(chunkX, chunkZ));
        return taken != null ? taken : inboxes.at(chunkX, chunkZ);
    }
}
