package fr.hardel.leafs.chunk.owner;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.global.GlobalScheduler;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

public final class ChunkOwners implements Router {
    public interface Regions {
        boolean live();

        @Nullable RegionInbox inboxAt(int chunkX, int chunkZ);

        @Nullable Thread tickerAt(int chunkX, int chunkZ);
    }

    @FunctionalInterface
    public interface Taker {
        boolean take(int chunkX, int chunkZ, Runnable task);
    }

    private final ChunkPool pool;
    private final ChunkPlacement placement;
    private final Regions regions;
    private final Thread serverThread;
    private final Executor serial;
    private final Taker taker;
    private final GlobalScheduler server;
    private final ConcurrentLong2ObjectMap<ChunkClaim> borrowed = new ConcurrentLong2ObjectMap<>();

    public ChunkOwners(ChunkPool pool, ChunkPlacement placement, Regions regions, Thread serverThread, Executor serial, Taker taker, GlobalScheduler server) {
        this.pool = pool;
        this.placement = placement;
        this.regions = regions;
        this.serverThread = serverThread;
        this.serial = serial;
        this.taker = taker;
        this.server = server;
    }

    public boolean submit(int chunkX, int chunkZ, Work work, Runnable task) {
        if (holds(chunkX, chunkZ) && !ChunkLevels.draining()) {
            task.run();
            return true;
        }

        if (!regions.live()) {
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

    public void publish(int chunkX, int chunkZ, Runnable task) {
        if (holds(chunkX, chunkZ) || !regions.live()) {
            submit(chunkX, chunkZ, Work.CHUNK, task);
            return;
        }

        placement.onPool(ChunkTask.Kind.OWNER, chunkX, chunkZ, 0, () -> publishOnPool(chunkX, chunkZ, task));
    }

    public void later(int chunkX, int chunkZ, Work work, Runnable task) {
        if (!regions.live()) {
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
        placement.onPool(ChunkTask.Kind.OWNER, chunkX, chunkZ, 0, () -> onPoolStart(chunkX, chunkZ, task));
    }

    public boolean holds(int chunkX, int chunkZ) {
        ChunkClaim claim = claimAt(chunkX, chunkZ);
        if (claim != null) {
            return claim.mine();
        }

        Thread owner = regions.live() ? regions.tickerAt(chunkX, chunkZ) : serverThread;
        return owner == Thread.currentThread();
    }

    public boolean covered(int chunkX, int chunkZ) {
        return regions.inboxAt(chunkX, chunkZ) != null;
    }

    public boolean heldElsewhere(int chunkX, int chunkZ) {
        ChunkClaim taken = claimAt(chunkX, chunkZ);
        return taken != null && !taken.mine();
    }

    public @Nullable ChunkClaim claimAt(int chunkX, int chunkZ) {
        return borrowed.get(ChunkPos.pack(chunkX, chunkZ));
    }

    public Executor executor(int chunkX, int chunkZ) {
        return task -> submit(chunkX, chunkZ, Work.CHUNK, task);
    }

    public @Nullable ChunkClaim borrow(int chunkX, int chunkZ) {
        ChunkClaim claim = new ChunkClaim(Thread.currentThread(), new RegionInbox());
        return borrowed.putIfAbsent(ChunkPos.pack(chunkX, chunkZ), claim) == null ? claim : null;
    }

    public void release(int chunkX, int chunkZ, ChunkClaim claim) {
        borrowed.remove(ChunkPos.pack(chunkX, chunkZ), claim);
        resubmit(claim.mail());
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

            ChunkClaim claim = borrow(chunkX, chunkZ);
            if (claim != null) {
                runClaimed(chunkX, chunkZ, claim, task);
                return;
            }
        }
    }

    private void publishOnPool(int chunkX, int chunkZ, Runnable task) {
        ChunkClaim claim = claimBetweenTicks(chunkX, chunkZ);
        if (claim == null) {
            onPoolStart(chunkX, chunkZ, task);
            return;
        }

        runClaimed(chunkX, chunkZ, claim, task);
    }

    private @Nullable ChunkClaim claimBetweenTicks(int chunkX, int chunkZ) {
        ChunkClaim claim = borrow(chunkX, chunkZ);
        if (claim == null || regions.tickerAt(chunkX, chunkZ) == null) {
            return claim;
        }

        release(chunkX, chunkZ, claim);
        return null;
    }

    private void runClaimed(int chunkX, int chunkZ, ChunkClaim claim, Runnable task) {
        try {
            task.run();
        } finally {
            release(chunkX, chunkZ, claim);
        }
    }

    private @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
        ChunkClaim taken = claimAt(chunkX, chunkZ);
        return taken != null ? taken.mail() : regions.inboxAt(chunkX, chunkZ);
    }
}
