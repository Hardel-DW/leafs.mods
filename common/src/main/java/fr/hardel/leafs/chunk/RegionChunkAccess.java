package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.holder.ChunkWait;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class RegionChunkAccess {
    private RegionChunkAccess() {
    }

    public static LevelChunk fullChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        return fullChunkOrNull(chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ)));
    }

    public static LevelChunk levelChunkOrNull(ChunkMap chunkMap, int chunkX, int chunkZ) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess latest = holder == null ? null : holder.getLatestChunk();
        return switch (latest) {
            case LevelChunk chunk -> chunk;
            case ImposterProtoChunk imposter -> imposter.getWrapped();
            case null, default -> null;
        };
    }

    public static LevelChunk fullChunkOrNull(ChunkHolder holder) {
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    public static boolean fullAround(ChunkMap chunkMap, int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (fullChunkOrNull(chunkMap, chunkX + dx, chunkZ + dz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public static ChunkAccess presentChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        return holder == null ? null : holder.getChunkIfPresent(status);
    }

    public static ChunkAccess contractedChunk(ChunkMap chunkMap, int chunkX, int chunkZ, ChunkStatus status, boolean required) {
        ChunkAccess chunk = presentChunk(chunkMap, chunkX, chunkZ, status);
        if (chunk != null || !required) {
            return chunk;
        }

        return ChunkWait.chunk(chunkMap.level, chunkX, chunkZ, status);
    }
}
