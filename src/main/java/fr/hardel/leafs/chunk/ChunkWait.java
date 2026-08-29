package fr.hardel.leafs.chunk;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** A required chunk that is not there: the ticket goes in, then the thread waits for the delivery the way it may. A region takes the chunk's region first and drains mail meanwhile, the server thread pumps as vanilla, any other thread just waits. A chunk no region owns is delivered by the workers. */
public final class ChunkWait {
    private static final long PARK_NANOS = 50_000L;

    private ChunkWait() {
    }

    public static ChunkAccess chunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status) {
        LevelRegions regions = LevelRegions.of(chunkMap.level);
        Region<RegionTickData> owner = regions.regionizer().regionAt(chunkX, chunkZ);
        WorldTickContext mine = WorldTickContext.current();
        if (owner == null || (mine != null && owner == mine.region())) {
            return awaitDelivery(chunkMap, chunkX, chunkZ, status);
        }

        return RegionBorrow.hold(mine == null ? null : mine.region(), borrow -> {
            borrow.borrow(regions, chunkX, chunkZ);
            return awaitDelivery(chunkMap, chunkX, chunkZ, status);
        });
    }

    /** A completion this thread needs, waited on the same way as a chunk. */
    public static void until(ServerLevel level, BooleanSupplier done) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        if (level.getServer().isSameThread()) {
            level.getServer().managedBlock(done);
            return;
        }

        while (!done.getAsBoolean()) {
            drainMail(chunkMap);
            LockSupport.parkNanos(PARK_NANOS);
        }
    }

    private static ChunkAccess awaitDelivery(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status) {
        CompletableFuture<?> delivery = ChunkDemands.demand(chunkMap, status, LongList.of(ChunkPos.pack(chunkX, chunkZ)));
        until(chunkMap.level, () -> RegionChunkAccess.presentChunk(chunkMap, chunkX, chunkZ, status) != null || (delivery != null && delivery.isDone()));
        ChunkAccess chunk = RegionChunkAccess.presentChunk(chunkMap, chunkX, chunkZ, status);
        if (chunk == null) {
            throw new IllegalStateException("Chunk [" + chunkX + ", " + chunkZ + "] was delivered without reaching " + status);
        }

        return chunk;
    }

    /** The waiting region's own mail, so a promotion routed to itself completes, and the mail of what it holds. */
    private static void drainMail(ChunkMap chunkMap) {
        WorldTickContext mine = WorldTickContext.current();
        if (mine != null) {
            ChunkMap own = mine.level().getChunkSource().chunkMap;
            RegionChunkAccess.scheduling(own).mailbox().drain(mine.region(), own);
        }

        RegionBorrow borrow = RegionBorrow.current();
        if (borrow != null) {
            borrow.drainMail(RegionChunkAccess.scheduling(chunkMap).mailbox(), chunkMap);
        }
    }
}
