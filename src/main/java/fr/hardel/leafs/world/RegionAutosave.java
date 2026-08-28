package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.region.Region;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Region autosave driven by the level's epoch: players and chunks behind the epoch save on their owner, twenty chunks per tick, plus vanilla's eager saves. */
public final class RegionAutosave {
    private final ChunkSaves saves;

    public RegionAutosave(ServerLevel level) {
        this.saves = new ChunkSaves(level);
    }

    /** Runs while TICKING on the owner, where the chunk walk and the entity photo are legal. A forced epoch saves every chunk behind it in this pass. */
    public void tick(Region<?> region, RegionChunks chunks, RegionEntities entities, long epoch, boolean forced) {
        saves.saveEagerly(chunkKey -> region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                saves.saveBehindEpoch(player, epoch);
            }
        });

        int budget = forced ? Integer.MAX_VALUE : ChunkSaves.CHUNKS_PER_TICK;
        for (ChunkHolder holder : chunks.holders()) {
            if (saves.saveBehindEpoch(holder, epoch) && --budget == 0) {
                return;
            }
        }
    }
}
