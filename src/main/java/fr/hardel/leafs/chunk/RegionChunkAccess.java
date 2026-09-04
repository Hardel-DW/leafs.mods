package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.holder.ChunkWait;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionContext;
import fr.hardel.leafs.world.WorldTickContext;
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

    /** Presence, never the ticket level: a ticket only says the chunk is due. */
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

        return ChunkWait.chunk(chunkMap.level, chunkX, chunkZ, status);
    }

    /** Vanilla's readiness of a chunk for the client, whoever owns it: what decides that a chunk entering a view joins the send queue. */
    public static LevelChunk readyToSend(ChunkMap chunkMap, long key) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(key);
        return holder == null ? null : holder.getChunkToSend();
    }

    /** A region serializes its own chunks and the chunks no region covers; another region's chunk stays in the send queue until ownership converges. */
    public static boolean sendable(ChunkMap chunkMap, long key) {
        if (!(RegionContext.current() instanceof RegionContext.Region)) {
            return true;
        }

        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        return WorldTickContext.ownsChunk(chunkMap.level, chunkX, chunkZ) || LevelRegions.of(chunkMap.level).regionizer().regionAt(chunkX, chunkZ) == null;
    }
}
