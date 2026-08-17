package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
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

    /**
     * A refused required read files a short-lived ticket, drains it and requests the status, so the pool
     * loads the chunk and the vanilla retry finds it. The ticket alone would only pin a holder: a level
     * like 41 for STRUCTURE_STARTS triggers no promotion, which is what left structure spawn positions
     * unloadable forever (roadmap 11).
     */
    public static ChunkAccess presentChunkOrThrow(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(status);
        if (chunk != null || !required) {
            return chunk;
        }

        chunkMap.level.getChunkSource().ticketStorage.addTicket(new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status)), new ChunkPos(chunkX, chunkZ));
        LevelTicketPropagator propagator = ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator();
        propagator.drain();
        propagator.scheduling().requestStatus(chunkX, chunkZ, status);

        throw new OwnershipViolationException("Chunk [" + chunkX + ", " + chunkZ + "] not present at " + status + " in the visible map: a region worker cannot sync-load it");
    }
}
