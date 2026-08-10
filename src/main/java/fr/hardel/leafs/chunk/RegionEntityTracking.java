package fr.hardel.leafs.chunk;

import fr.hardel.leafs.entity.RegionEntityTickList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Section-crossing pairing against players of other regions is covered by the level-serial {@code ChunkMap.move} pass. */
public final class RegionEntityTracking {

    private RegionEntityTracking() {
    }

    public static void tickSerial(ChunkMap chunkMap) {
        for (ServerPlayer player : chunkMap.playerMap.getAllPlayers()) {
            chunkMap.updateChunkTracking(player);
        }
    }

    public static void tickRegion(ServerLevel level, RegionEntityTickList<Entity> entities) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = chunkMap.entityMap;
        List<ServerPlayer> players = level.players();
        List<ServerPlayer> movedPlayers = new ArrayList<>();
        entities.forEach(entity -> {
            ChunkMap.TrackedEntity tracked = entityMap.get(entity.getId());
            if (tracked == null) {
                return;
            }

            SectionPos oldPos = tracked.lastSectionPos;
            SectionPos newPos = SectionPos.of(tracked.entity);
            boolean sectionChanged = !Objects.equals(oldPos, newPos);
            if (sectionChanged) {
                tracked.updatePlayers(players);
                if (tracked.entity instanceof ServerPlayer player) {
                    movedPlayers.add(player);
                }

                tracked.lastSectionPos = newPos;
            }

            if (sectionChanged || tracked.entity.needsSync || chunkMap.getDistanceManager().inEntityTickingRange(newPos.chunk().pack())) {
                tracked.serverEntity.sendChanges();
            }
        });
        if (!movedPlayers.isEmpty()) {
            entities.forEach(entity -> {
                ChunkMap.TrackedEntity tracked = entityMap.get(entity.getId());
                if (tracked != null) {
                    tracked.updatePlayers(movedPlayers);
                }
            });
        }

        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                chunkMap.updateChunkTracking(player);
            }
        });
    }
}
