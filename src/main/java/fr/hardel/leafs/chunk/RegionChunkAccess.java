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
 * The chunk contract, decided here and never at the call site. Any thread may look at the published
 * world: a peek answers from presence, foreign chunks included, because block state reads ride the
 * volatile snapshot of the palette container. Only loading stays with the owner: a required read of
 * an absent chunk refuses with a demand ticket so a later attempt finds it, and a required read of a
 * foreign chunk refuses without a ticket, because one would pin a holder that owner controls.
 */
public final class RegionChunkAccess {

    private RegionChunkAccess() {
    }

    /** The peek form of the contract, backing {@code getChunkNow} and {@code hasChunk} from any thread. */
    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    /** Presence, never the ticket level: a ticket only says the chunk is DUE, which is vanilla hasChunk's lie. */
    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /**
     * The full form of the contract. A peek answers from presence for every thread. A refused
     * required read of an ABSENT chunk files a short-lived ticket, drains it and requests the
     * status, so the pool loads the chunk and a later attempt finds it; the ticket alone would only
     * pin a holder (roadmap 11). A required read of a FOREIGN chunk files nothing and names the
     * owning region, so the log line is attributable.
     */
    public static ChunkAccess contractedChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(status);
        if (!required) {
            return chunk;
        }

        ChunkScheduling scheduling = scheduling(chunkMap);
        if (!scheduling.isOwner(chunkX, chunkZ)) {
            scheduling.deferStats().countRefusal(OwnershipViolationException.Kind.FOREIGN, sourceOfCurrentThread());
            throw new OwnershipViolationException(OwnershipViolationException.Kind.FOREIGN,
                "Chunk [" + chunkX + ", " + chunkZ + "] is owned by another region than " + RegionContext.current() + ": crossing costs a refusal, never a lock");
        }

        if (chunk != null) {
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
