package fr.hardel.leafs.chunk;

import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.entity.RegionEntities;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tracking split by owner: the entity pass runs on the owning region, against every player that moved since its last pass. */
public final class RegionEntityTracking {

    private RegionEntityTracking() {
    }

    public static void tickSerial(ChunkMap chunkMap) {
        for (ServerPlayer player : chunkMap.playerMap.getAllPlayers()) {
            chunkMap.updateChunkTracking(player);
        }
    }

    public static void tickRegion(ServerLevel level, RegionEntities entities) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = chunkMap.entityMap;
        List<ServerPlayer> players = level.players();
        List<ServerPlayer> moved = movedSince(players, entities.lastTrackingNanos());
        entities.markTracking(System.nanoTime());
        entities.forEach(entity -> {
            ChunkMap.TrackedEntity tracked = entityMap.get(entity.getId());
            if (tracked == null) {
                return;
            }

            SectionPos oldPos = tracked.lastSectionPos;
            SectionPos newPos = SectionPos.of(tracked.entity);
            boolean sectionChanged = !Objects.equals(oldPos, newPos);
            if (sectionChanged || (entity instanceof ServerPlayer player && moved.contains(player))) {
                tracked.updatePlayers(players);
                tracked.lastSectionPos = newPos;
            } else {
                for (ServerPlayer player : moved) {
                    tracked.updatePlayer(player);
                }
            }

            if (sectionChanged || tracked.entity.needsSync || chunkMap.getDistanceManager().inEntityTickingRange(newPos.chunk().pack())) {
                tracked.serverEntity.sendChanges();
            }
        });

        entities.forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                chunkMap.updateChunkTracking(player);
            }
        });
    }

    private static List<ServerPlayer> movedSince(List<ServerPlayer> players, long sinceNanos) {
        List<ServerPlayer> moved = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (((PlayerMoveAccess) player).leafs$movedNanos() >= sinceNanos) {
                moved.add(player);
            }
        }

        return moved;
    }
}
