package fr.hardel.leafs.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RegionAutosave {
    private final ChunkSaves saves;

    public RegionAutosave(ServerLevel level) {
        this.saves = new ChunkSaves(level);
    }

    public void tick(RegionWorldData worldData, long epoch, long deadline) {
        saves.saveEagerly(worldData.chunks().holders(), deadline);
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
