package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Region autosave driven by the level's epoch: players and chunks behind the epoch save on their owner, in the slice of the tick the save is allowed, plus vanilla's eager saves. */
public final class RegionAutosave {
    private final ChunkSaves saves;

    public RegionAutosave(ServerLevel level) {
        this.saves = new ChunkSaves(level);
    }

    /** Runs while TICKING on the owner, where the chunk walk and the entity photo are legal. The walk stops at the deadline and resumes next tick; once through, the epoch is done for this region until the next one. */
    public void tick(Region<?> region, RegionWorldData worldData, long epoch, long deadline) {
        saves.saveEagerly(key -> region.owns(ChunkPos.getX(key), ChunkPos.getZ(key)), deadline);
        worldData.entities().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                saves.saveBehindEpoch(player, epoch);
            }
        });

        if (worldData.savedEpoch() == epoch) {
            return;
        }

        for (ChunkHolder holder : worldData.chunks().holders()) {
            if (saves.saveBehindEpoch(holder, epoch) && System.nanoTime() >= deadline) {
                return;
            }
        }

        worldData.markEpochSaved(epoch);
    }
}
