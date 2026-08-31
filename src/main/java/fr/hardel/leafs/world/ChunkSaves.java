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

import java.util.List;

/** The save primitives an owner applies to its own chunks and players: vanilla's eager saves, and the epoch walk that visits everything once per autosave. */
public final class ChunkSaves {
    public static final int CHUNKS_PER_TICK = 20;

    private final ServerLevel level;

    public ChunkSaves(ServerLevel level) {
        this.level = level;
    }

    /** Vanilla's saveChunksEagerly over the caller's holders: the dirty ones whose save cadence elapsed, twenty per tick. */
    public void saveEagerly(List<ChunkHolder> holders) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        long now = Util.getMillis();
        int saved = 0;
        for (ChunkHolder holder : holders) {
            if (saved == CHUNKS_PER_TICK) {
                return;
            }

            ChunkAccess chunk = holder.getLatestChunk();
            if (chunk == null || !chunk.isUnsaved()) {
                chunkMap.chunksToEagerlySave.remove(holder.getPos().pack());
            } else if (chunkMap.saveChunkIfNeeded(holder, now)) {
                chunkMap.chunksToEagerlySave.remove(holder.getPos().pack());
                saved++;
            }
        }
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
