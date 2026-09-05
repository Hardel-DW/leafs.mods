package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** A required chunk that is not there: the thread asks for it and runs what it owns until it lands. The server thread borrows first. */
public final class ChunkWait {
    private static final long PARK_NANOS = 50_000L;
    private static final ConcurrentHashMap<Thread, WaitReport> WAITING = new ConcurrentHashMap<>();

    private ChunkWait() {
    }

    public static ChunkAccess chunk(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status) {
        if (WorldTickContext.current() != null) {
            return await(level, chunkX, chunkZ, status);
        }

        return RegionBorrow.hold(borrow -> {
            borrow.borrow(LevelRegions.of(level), chunkX, chunkZ);
            return await(level, chunkX, chunkZ, status);
        });
    }

    /** The server thread pumps, its borrowed inboxes with it; a region drains its own inbox, so a promotion routed to itself completes. */
    public static void until(ServerLevel level, BooleanSupplier done) {
        if (level.getServer().isSameThread()) {
            level.getServer().managedBlock(done);
            return;
        }

        WorldTickContext mine = WorldTickContext.current();
        while (!done.getAsBoolean()) {
            if (mine != null) {
                mine.region().data().inbox().drain();
            }

            LockSupport.parkNanos(PARK_NANOS);
        }
    }

    public static @Nullable String describe(Thread thread) {
        WaitReport report = WAITING.get(thread);
        return report == null ? null : report.toString();
    }

    /** The first frame that is neither the wait nor the chunk read it serves: the game code that needed the chunk. */
    private static String asker() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.startsWith("java.") || owner.startsWith("fr.hardel.leafs.chunk.") || owner.endsWith("ServerChunkCache") || owner.endsWith(".Level") || owner.endsWith("LevelReader")) {
                continue;
            }

            return owner.substring(owner.lastIndexOf('.') + 1) + "." + frame.getMethodName();
        }

        return "unknown";
    }

    private static ChunkAccess await(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status) {
        CompletableFuture<ChunkResult<ChunkAccess>> delivery = LevelChunks.of(level).holders().require(chunkX, chunkZ, status);
        WaitReport report = new WaitReport(level, chunkX, chunkZ, status, delivery);
        WaitReport outer = WAITING.put(Thread.currentThread(), report);
        String found = report.toString();
        long started = System.nanoTime();
        try {
            until(level, delivery::isDone);
        } finally {
            long waited = System.nanoTime() - started;
            TickingManager ticking = TickingManager.of(level.getServer());
            ticking.metrics().chunkWaited(waited);
            if (waited >= ticking.slowTaskWarnMillis() * 1_000_000L) {
                Leafs.LOGGER.warn("Waited {} ms for a chunk, asked by {}, found {}", waited / 1_000_000L, asker(), found);
            }

            if (outer == null) {
                WAITING.remove(Thread.currentThread());
            } else {
                WAITING.put(Thread.currentThread(), outer);
            }
        }

        ChunkResult<ChunkAccess> result = delivery.join();
        return result.orElseThrow(() -> new IllegalStateException("Chunk [%d, %d] was not delivered at %s: %s, tickets %s".formatted(
            chunkX, chunkZ, status, result.getError(), level.getChunkSource().ticketStorage.getTicketDebugString(ChunkPos.pack(chunkX, chunkZ), false))));
    }
}
