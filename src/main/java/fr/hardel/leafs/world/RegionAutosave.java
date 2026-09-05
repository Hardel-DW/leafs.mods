package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntities;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Region autosave driven by the level's epoch: players and chunks behind the epoch save on their owner, in the slice of the tick the save is allowed, plus vanilla's eager saves. */
public final class RegionAutosave {
    private final ChunkSaves saves;

    public RegionAutosave(ServerLevel level) {
        this.saves = new ChunkSaves(level);
    }

    /** Runs while TICKING on the owner, where the chunk walk and the entity photo are legal. A forced epoch saves every chunk behind it in this pass, whatever the slice. */
    public void tick(RegionChunks chunks, RegionEntities entities, long epoch, boolean forced, long deadlineNanos) {
        long deadline = forced ? Long.MAX_VALUE : deadlineNanos;
        saves.saveEagerly(chunks.holders(), deadline);
        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                saves.saveBehindEpoch(player, epoch);
            }
        });

        for (ChunkHolder holder : chunks.holders()) {
            if (saves.saveBehindEpoch(holder, epoch) && System.nanoTime() >= deadline) {
                return;
            }
        }
    }
}
