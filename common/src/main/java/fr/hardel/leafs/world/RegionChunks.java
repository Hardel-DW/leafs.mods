package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.region.Region;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public final class RegionChunks {
    private final List<ChunkHolder> holders = new ArrayList<>();
    private final List<LevelChunk> ticking = new ArrayList<>();
    private final List<LevelChunk> simulated = new ArrayList<>();
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    public void refresh(Region<?> region, ChunkMap chunkMap) {
        holders.clear();
        ticking.clear();
        simulated.clear();
        minX = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxZ = Integer.MIN_VALUE;
        LevelChunks chunks = LevelChunks.of(chunkMap.level);
        HolderTable table = chunks.holders().table();
        ChunkOwners owners = chunks.owners();
        for (long section : region.sectionKeySnapshot()) {
            table.forEachHolderIn(section, holder -> collect(holder, owners, chunkMap.level));
        }
    }

    private void collect(ChunkHolder holder, ChunkOwners owners, ServerLevel level) {
        ChunkPos pos = holder.getPos();
        if (owners.heldElsewhere(pos.x(), pos.z()) || RegionChunkAccess.fullChunkOrNull(holder) == null) {
            return;
        }

        holders.add(holder);
        minX = Math.min(minX, pos.x());
        minZ = Math.min(minZ, pos.z());
        maxX = Math.max(maxX, pos.x());
        maxZ = Math.max(maxZ, pos.z());
        if (!ChunkLevel.isBlockTicking(holder.getTicketLevel())) {
            return;
        }

        LevelChunk chunk = holder.getTickingChunk();
        if (chunk == null) {
            return;
        }

        ticking.add(chunk);
        if (level.shouldTickBlocksAt(pos.pack())) {
            simulated.add(chunk);
        }
    }

    public List<ChunkHolder> holders() {
        return holders;
    }

    public List<LevelChunk> ticking() {
        return ticking;
    }

    /** The ticking chunks inside the simulation distance of a player or of a forced ticket: the ones whose blocks tick. */
    public List<LevelChunk> simulated() {
        return simulated;
    }

    public boolean within(ChunkPos chunk, int margin) {
        return chunk.x() >= minX - margin && chunk.x() <= maxX + margin && chunk.z() >= minZ - margin && chunk.z() <= maxZ + margin;
    }
}
