package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.ThreadWaits;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ChunkWait {
    private ChunkWait() {
    }

    public static ChunkAccess chunk(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status) {
        WorldTickContext tick = WorldTickContext.current();
        if (tick != null) {
            return await(level, chunkX, chunkZ, status, tick::keep);
        }

        return RegionBorrow.hold(borrow -> {
            borrow.borrow(LevelRegions.of(level), chunkX, chunkZ);
            return await(level, chunkX, chunkZ, status, borrow::keep);
        });
    }

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

    private static ChunkAccess await(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status, Consumer<Runnable> keep) {
        ChunkHolders.Demand demand = LevelChunks.of(level).holders().require(chunkX, chunkZ, status);
        CompletableFuture<ChunkResult<ChunkAccess>> delivery = demand.delivery();
        WaitReport report = new WaitReport(level, chunkX, chunkZ, status, delivery, System.nanoTime());
        ThreadWaits.Wait outer = ThreadWaits.open(report::toString);
        TickingManager ticking = TickingManager.of(level.getServer());
        String found = ticking.slowTaskNanos() == Long.MAX_VALUE ? null : report.toString();
        try {
            ticking.await(delivery::isDone);
        } finally {
            keep.accept(demand.release());
            long waited = System.nanoTime() - report.startedNanos();
            ticking.metrics().chunkWaited(waited);
            if (waited >= ticking.slowTaskNanos()) {
                Leafs.LOGGER.warn("Waited {} ms for a chunk, asked by {}, found {}", waited / 1_000_000L, asker(), found);
            }

            ThreadWaits.close(outer);
        }

        ChunkResult<ChunkAccess> result = delivery.join();
        return result.orElseThrow(() -> new IllegalStateException("Chunk [%d, %d] was not delivered at %s: %s, tickets %s".formatted(
            chunkX, chunkZ, status, result.getError(), level.getChunkSource().ticketStorage.getTicketDebugString(ChunkPos.pack(chunkX, chunkZ), false))));
    }
}
