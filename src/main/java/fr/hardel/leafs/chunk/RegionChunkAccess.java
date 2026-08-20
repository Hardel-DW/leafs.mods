package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.ownership.RegionContext;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;

// The chunk contract, decided here, never at call sites: any thread reads what is published; a required read of an absent chunk refuses with a demand ticket, of a foreign chunk without one.
public final class RegionChunkAccess {

    private RegionChunkAccess() {
    }

    // The peek form, backing getChunkNow and hasChunk from any thread.
    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    // Presence, never the ticket level: a ticket only says the chunk is DUE, vanilla hasChunk's lie.
    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    // The full form: peek serves everyone; required ABSENT demands and carries readiness, required FOREIGN names the owner so the log line is attributable.
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

        CompletableFuture<?> readiness = ChunkDemands.demand(chunkMap, status, LongList.of(ChunkPos.pack(chunkX, chunkZ)));
        scheduling.deferStats().countRefusal(OwnershipViolationException.Kind.ABSENT, sourceOfCurrentThread());

        throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT,
            "Chunk [" + chunkX + ", " + chunkZ + "] not present at " + status + " in the visible map: a demand ticket is filed, this thread cannot sync-load it",
            readiness);
    }

    static ChunkScheduling scheduling(ChunkMap chunkMap) {
        return ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }

    static DeferStats.RefusalSource sourceOfCurrentThread() {
        if (RegionContext.current() instanceof RegionContext.Region) {
            return DeferStats.RefusalSource.REGION;
        }

        return DegradedChunkReads.active() ? DeferStats.RefusalSource.SERIAL : DeferStats.RefusalSource.FOREIGN_THREAD;
    }
}
