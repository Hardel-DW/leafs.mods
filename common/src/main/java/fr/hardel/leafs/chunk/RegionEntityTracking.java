package fr.hardel.leafs.chunk;

import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.entity.RegionEntities;
import fr.hardel.leafs.world.RegionChunks;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tracking split by owner: a region checks its entities against the players within view of its chunks, plus the ones already seeing an entity so a departure unpairs. */
public final class RegionEntityTracking {
    private RegionEntityTracking() {
    }

    public static void tickRegion(ServerLevel level, RegionChunks chunks, RegionEntities entities) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = chunkMap.entityMap;
        long since = entities.lastTrackingNanos();
        entities.markTracking(System.nanoTime());
        int reach = chunkMap.serverViewDistance + 1;
        List<ServerPlayer> near = new ArrayList<>();
        List<ServerPlayer> nearMoved = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (chunks.within(player.chunkPosition(), reach)) {
                near.add(player);
                if (movedSince(player, since)) {
                    nearMoved.add(player);
                }
            }
        }

        List<ServerPlayer> watchers = new ArrayList<>();
        entities.forEach(entity -> {
            ChunkMap.TrackedEntity tracked = entityMap.get(entity.getId());
            if (tracked == null) {
                return;
            }

            SectionPos oldPos = tracked.lastSectionPos;
            SectionPos newPos = SectionPos.of(tracked.entity);
            boolean sectionChanged = !Objects.equals(oldPos, newPos);
            boolean everyone = sectionChanged || (entity instanceof ServerPlayer player && movedSince(player, since));
            for (ServerPlayer player : everyone ? near : nearMoved) {
                tracked.updatePlayer(player);
            }

            farWatchers(tracked, chunks, reach, watchers);
            for (ServerPlayer player : watchers) {
                if (everyone || movedSince(player, since)) {
                    tracked.updatePlayer(player);
                }
            }

            if (sectionChanged) {
                tracked.lastSectionPos = newPos;
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

    /** The players seeing the entity from beyond the region's reach; copied first, an update may unpair them from the set. */
    private static void farWatchers(ChunkMap.TrackedEntity tracked, RegionChunks chunks, int reach, List<ServerPlayer> watchers) {
        watchers.clear();
        for (ServerPlayerConnection connection : tracked.seenBy) {
            ServerPlayer player = connection.getPlayer();
            if (!chunks.within(player.chunkPosition(), reach)) {
                watchers.add(player);
            }
        }
    }

    private static boolean movedSince(ServerPlayer player, long sinceNanos) {
        return ((PlayerMoveAccess) player).leafs$movedNanos() >= sinceNanos;
    }
}
