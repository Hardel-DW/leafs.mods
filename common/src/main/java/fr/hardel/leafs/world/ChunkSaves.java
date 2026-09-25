package fr.hardel.leafs.world;

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

public final class ChunkSaves {
    private final ServerLevel level;

    public ChunkSaves(ServerLevel level) {
        this.level = level;
    }

    public void autosave(RegionWorldData worldData, long epoch, long deadlineNanos) {
        List<ChunkHolder> holders = worldData.chunks().holders();
        LongSet dirty = level.getChunkSource().chunkMap.chunksToEagerlySave;
        for (ChunkHolder holder : holders) {
            if (dirty.contains(holder.getPos().pack()) && saveEagerly(holder) && System.nanoTime() >= deadlineNanos) {
                break;
            }
        }

        worldData.entities().forEach(entity -> {
            if (entity instanceof ServerPlayer player && ((SavedEpochAccess) player).leafs$savedEpoch() < epoch) {
                level.getServer().getPlayerList().save(player);
                ((SavedEpochAccess) player).leafs$markSaved(epoch);
            }
        });

        if (worldData.savedEpoch() == epoch) {
            return;
        }

        for (ChunkHolder holder : holders) {
            if (saveBehindEpoch(holder, epoch) && System.nanoTime() >= deadlineNanos) {
                return;
            }
        }

        worldData.markEpochSaved(epoch);
    }

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

    public boolean saveBehindEpoch(ChunkHolder holder, long epoch) {
        SavedEpochAccess access = (SavedEpochAccess) holder;
        if (access.leafs$savedEpoch() >= epoch) {
            return false;
        }

        RegionEntityPersistence persistence = ((ServerLevelEntityAccess) level).leafs$entityPersistence();
        level.getChunkSource().chunkMap.saveChunkIfNeeded(holder, Util.getMillis());
        level.getPoiManager().flush(holder.getPos());
        persistence.saveChunkOnOwner(holder.getPos().pack());
        access.leafs$markSaved(epoch);
        return true;
    }
}
