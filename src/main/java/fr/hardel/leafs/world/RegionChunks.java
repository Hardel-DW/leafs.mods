package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** The region's chunks this tick, taken at tick start: every visible holder, the ticking chunks among them, and the box they span. Every phase walks these two lists. */
public final class RegionChunks {
    private final List<ChunkHolder> holders = new ArrayList<>();
    private final List<LevelChunk> ticking = new ArrayList<>();
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    public void refresh(Region<?> region, ChunkMap chunkMap) {
        holders.clear();
        ticking.clear();
        minX = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxZ = Integer.MIN_VALUE;
        region.forEachChunk((chunkX, chunkZ) -> {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
            if (holder == null) {
                return;
            }

            holders.add(holder);
            minX = Math.min(minX, chunkX);
            minZ = Math.min(minZ, chunkZ);
            maxX = Math.max(maxX, chunkX);
            maxZ = Math.max(maxZ, chunkZ);
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk != null) {
                ticking.add(chunk);
            }
        });
    }

    public List<ChunkHolder> holders() {
        return holders;
    }

    public List<LevelChunk> ticking() {
        return ticking;
    }

    /** Whether the chunk lies within {@code margin} chunks of the box the region's loaded chunks span. */
    public boolean within(ChunkPos chunk, int margin) {
        return chunk.x() >= minX - margin && chunk.x() <= maxX + margin && chunk.z() >= minZ - margin && chunk.z() <= maxZ + margin;
    }
}
