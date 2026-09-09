package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.SavedEpochAccess;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.ChunkAccess;

import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

/** The save primitives an owner applies to its own chunks and players: vanilla's eager saves, and the epoch walk that visits everything once per autosave. */
public final class ChunkSaves {
    /** The lot of a pool sweep pass over the chunks no region covers. */
    public static final int CHUNKS_PER_TICK = 20;

    private final ServerLevel level;

    public ChunkSaves(ServerLevel level) {
        this.level = level;
    }

    /** Vanilla's saveChunksEagerly over the owner's chunks that sit in the level's dirty set, until the deadline: the owner walks its own chunks, never the whole set. */
    public void saveEagerly(List<ChunkHolder> holders, long deadlineNanos) {
        LongSet dirty = level.getChunkSource().chunkMap.chunksToEagerlySave;
        for (ChunkHolder holder : holders) {
            if (dirty.contains(holder.getPos().pack()) && saveEagerly(holder) && System.nanoTime() >= deadlineNanos) {
                return;
            }
        }
    }

    /** One dirty chunk whose save cadence elapsed; a clean one leaves the eager set. */
    public boolean saveEagerly(ChunkHolder holder) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        long key = holder.getPos().pack();
        ChunkAccess chunk = holder.getLatestChunk();
        if (chunk == null || !chunk.isUnsaved()) {
            chunkMap.chunksToEagerlySave.remove(key);
            return false;
        }

        if (!chunkMap.saveChunkIfNeeded(holder, Util.getMillis())) {
            return false;
        }

        chunkMap.chunksToEagerlySave.remove(key);
        return true;
    }

    /** False when the holder already saved this epoch; true after its chunk and entity chunk went out, vanilla's autosave for one chunk. */
    public boolean saveBehindEpoch(ChunkHolder holder, long epoch) {
        SavedEpochAccess access = (SavedEpochAccess) holder;
        if (access.leafs$savedEpoch() >= epoch) {
            return false;
        }

        RegionEntityPersistence persistence = ((ServerLevelEntityAccess) level).leafs$entityPersistence();
        level.getChunkSource().chunkMap.saveChunkIfNeeded(holder, Util.getMillis());
        persistence.saveChunkOnOwner(holder.getPos().pack());
        access.leafs$markSaved(epoch);
        return true;
    }

    public void saveBehindEpoch(ServerPlayer player, long epoch) {
        SavedEpochAccess access = (SavedEpochAccess) player;
        if (access.leafs$savedEpoch() < epoch) {
            level.getServer().getPlayerList().save(player);
            access.leafs$markSaved(epoch);
        }
    }
}
