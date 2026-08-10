package fr.hardel.leafs.chunk;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Chunk reads for region workers: the visible-holder path only, never the main-thread cache and
 * never a sync load. A region reaches loaded chunks by the buffer invariant; asking for anything
 * else is an off-owner access and crashes instead of deadlocking on the serial pump.
 */
public final class RegionChunkAccess {

    private RegionChunkAccess() {
    }

    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    /** Presence, never the ticket level: a ticket only says the chunk is DUE, which is vanilla hasChunk's lie. */
    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /** A refused required read runs {@code demand} first, so the load gets filed and the vanilla retry converges. */
    public static ChunkAccess presentChunkOrThrow(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required, Runnable demand) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(status);
        if (chunk == null && required) {
            demand.run();
            throw new OwnershipViolationException("Chunk [" + chunkX + ", " + chunkZ + "] not present at " + status + " in the visible map: a region worker cannot sync-load it");
        }

        return chunk;
    }
}
