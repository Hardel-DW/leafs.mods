package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The chunk contract: every request from a thread that is not a universal owner gets one of three
 * answers, decided here and never at the call site. Mine, buffer included, passes. Absent refuses
 * with a demand ticket, so a later attempt finds the chunk. Foreign, loaded but owned by another
 * region, refuses without a ticket, because one would pin a holder that owner controls. Ownership is
 * tested before presence: a foreign chunk that happens to be present must still refuse.
 */
public final class RegionChunkAccess {

    private RegionChunkAccess() {
    }

    /**
     * Deliberately ownership-blind: this backs the region body's own loops over chunks it owns by
     * construction, where an ownership test per chunk per tick would buy no new refusal. The
     * companion mod's map reads through it too, a torn-read it tolerates by design.
     */
    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    /** Presence, never the ticket level: a ticket only says the chunk is DUE, which is vanilla hasChunk's lie. */
    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /** The peek form of the contract, backing {@code getChunkNow} and {@code hasChunk}: a foreign chunk answers absent. */
    public static LevelChunk fullOwnedChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return scheduling(chunkMap).isOwner(chunkX, chunkZ) ? fullChunkOrNull(chunkMap, chunkX, chunkZ) : null;
    }

    /**
     * The full form of the contract. A refused required read of an ABSENT chunk files a short-lived
     * ticket, drains it and requests the status, so the pool loads the chunk and a later attempt
     * finds it; the ticket alone would only pin a holder (roadmap 11). A FOREIGN refusal files
     * nothing and names the owning region, so the log line is attributable.
     */
    public static ChunkAccess contractedChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required) {
        ChunkScheduling scheduling = scheduling(chunkMap);
        if (!scheduling.isOwner(chunkX, chunkZ)) {
            if (!required) {
                return null;
            }

            scheduling.deferStats().countRefusal(OwnershipViolationException.Kind.FOREIGN, sourceOfCurrentThread());
            throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN,
                "Chunk [" + chunkX + ", " + chunkZ + "] is owned by another region than " + RegionContext.current() + ": crossing costs a refusal, never a lock");
        }

        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(status);
        if (chunk != null || !required) {
            return chunk;
        }

        chunkMap.level.getChunkSource().ticketStorage.addTicket(new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status)), new ChunkPos(chunkX, chunkZ));
        LevelTicketPropagator propagator = ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator();
        propagator.drain();
        propagator.scheduling().requestStatus(chunkX, chunkZ, status);
        scheduling.deferStats().countRefusal(OwnershipViolationException.Kind.ABSENT, sourceOfCurrentThread());

        throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT,
            "Chunk [" + chunkX + ", " + chunkZ + "] not present at " + status + " in the visible map: a demand ticket is filed, this thread cannot sync-load it");
    }

    private static ChunkScheduling scheduling(ChunkMap chunkMap) {
        return ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }

    private static DeferStats.RefusalSource sourceOfCurrentThread() {
        if (RegionContext.current() instanceof RegionContext.Region) {
            return DeferStats.RefusalSource.REGION;
        }

        return DegradedChunkReads.active() ? DeferStats.RefusalSource.SERIAL : DeferStats.RefusalSource.FOREIGN_THREAD;
    }
}
