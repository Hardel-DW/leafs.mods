package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** The region's chunks this tick, taken at tick start: every visible holder, and the ticking chunks among them. Every phase walks these two lists. */
public final class RegionChunks {
    private final List<ChunkHolder> holders = new ArrayList<>();
    private final List<LevelChunk> ticking = new ArrayList<>();

    public void refresh(Region<?> region, ChunkMap chunkMap) {
        holders.clear();
        ticking.clear();
        region.forEachChunk((chunkX, chunkZ) -> {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
            if (holder == null) {
                return;
            }

            holders.add(holder);
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
}
