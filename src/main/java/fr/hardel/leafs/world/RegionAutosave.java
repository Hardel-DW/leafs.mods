package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.SavedEpochAccess;
import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.Region;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Region autosave driven by the level's epoch: players and chunks behind the epoch save on their owner, twenty chunks per tick, plus vanilla's eager saves. */
public final class RegionAutosave {
    private static final int CHUNKS_PER_TICK = 20;

    private final ServerLevel level;

    public RegionAutosave(ServerLevel level) {
        this.level = level;
    }

    /** Runs while TICKING on the owner, where the chunk walk and the entity photo are legal. A forced epoch saves every chunk behind it in this pass. */
    public void tick(Region<?> region, RegionChunks chunks, RegionEntities entities, long epoch, boolean forced) {
        saveEagerly(region);
        savePlayers(entities, epoch);
        saveChunks(chunks, epoch, forced ? Integer.MAX_VALUE : CHUNKS_PER_TICK);
    }

    /** Vanilla's saveChunksEagerly over the region's own chunks: the dirty ones whose save cadence elapsed, twenty per tick. */
    private void saveEagerly(Region<?> region) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        long now = Util.getMillis();
        int saved = 0;
        for (LongIterator iterator = chunkMap.chunksToEagerlySave.iterator(); saved < CHUNKS_PER_TICK && iterator.hasNext(); ) {
            long chunkKey = iterator.nextLong();
            if (!region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey))) {
                continue;
            }

            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            ChunkAccess chunk = holder == null ? null : holder.getLatestChunk();
            if (chunk == null || !chunk.isUnsaved()) {
                iterator.remove();
            } else if (chunkMap.saveChunkIfNeeded(holder, now)) {
                saved++;
                iterator.remove();
            }
        }
    }

    private void savePlayers(RegionEntities entities, long epoch) {
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player && ((SavedEpochAccess) player).leafs$savedEpoch() < epoch) {
                level.getServer().getPlayerList().save(player);
                ((SavedEpochAccess) player).leafs$markSaved(epoch);
            }
        });
    }

    /** Vanilla's chunk and entity-chunk autosave, over the region's chunks still behind the epoch. */
    private void saveChunks(RegionChunks chunks, long epoch, int budget) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        RegionEntityPersistence persistence = ((ServerLevelEntityAccess) level).leafs$entityPersistence();
        long now = Util.getMillis();
        int saved = 0;
        for (ChunkHolder holder : chunks.holders()) {
            SavedEpochAccess access = (SavedEpochAccess) holder;
            if (access.leafs$savedEpoch() >= epoch) {
                continue;
            }

            chunkMap.saveChunkIfNeeded(holder, now);
            persistence.saveChunkOnOwner(holder.getPos().pack());
            access.leafs$markSaved(epoch);
            if (++saved == budget) {
                return;
            }
        }
    }
}
