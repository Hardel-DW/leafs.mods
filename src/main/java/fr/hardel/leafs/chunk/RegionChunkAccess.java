package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.chunk.core.ConcurrentChunkTable;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** The chunk contract, decided here, never at call sites: any thread reads what is published, and a required read of an absent chunk waits for it. */
public final class RegionChunkAccess {

    private RegionChunkAccess() {
    }

    /** The peek form, backing getChunkNow and hasChunk from any thread. */
    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    /** The chunk a block entity registers into: published full, or still inside its FULL step behind the imposter. */
    public static LevelChunk levelChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess latest = holder == null ? null : holder.getLatestChunk();
        return switch (latest) {
            case LevelChunk chunk -> chunk;
            case ImposterProtoChunk imposter -> imposter.getWrapped();
            case null, default -> null;
        };
    }

    /** Presence, never the ticket level: a ticket only says the chunk is DUE, vanilla hasChunk's lie. */
    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    public static ChunkAccess presentChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        return holder == null ? null : holder.getChunkIfPresent(status);
    }

    /** The full form: a published chunk serves every thread, a required absent one is waited for. */
    public static ChunkAccess contractedChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required) {
        ChunkAccess chunk = presentChunk(chunkMap, chunkX, chunkZ, status);
        if (chunk != null || !required) {
            return chunk;
        }

        return ChunkWait.chunk(chunkMap, chunkX, chunkZ, status);
    }

    /** The one holder table, read by section: the regions take their photo from it. */
    public static ConcurrentChunkTable holders(ChunkMap chunkMap) {
        return (ConcurrentChunkTable) chunkMap.updatingChunkMap;
    }

    public static ChunkScheduling scheduling(ChunkMap chunkMap) {
        return ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }
}
