package fr.hardel.leafs.network;

import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The global half of the send-chunks section, one snapshot per tick. The suspend pass captures the
 * players no region ticks, grouped by level; the send pass takes each level's exclusion once for
 * all its orphans instead of once per player, so a wave of orphans waits one region tick, not one
 * per player. Only the captured orphans resume-flush from the global thread, because a region
 * flushes its own players at its own cadence.
 */
public final class OrphanNetworkSweep {
    private final Map<ServerLevel, List<ServerPlayer>> orphansByLevel = new LinkedHashMap<>();
    private final List<ServerPlayer> orphans = new ArrayList<>();

    /** The suspend pass sees only this capture; suspend and resume stay balanced on the same snapshot. */
    public List<ServerPlayer> captureOrphans(List<ServerPlayer> players) {
        orphansByLevel.clear();
        orphans.clear();
        for (ServerPlayer player : players) {
            if (!RegionNetworkTick.ownedByRegion(player.connection)) {
                orphans.add(player);
                orphansByLevel.computeIfAbsent(player.level(), _ -> new ArrayList<>()).add(player);
            }
        }

        return orphans;
    }

    /** A player a region adopted or a disconnect removed since the capture skips his send; the claim re-reads under the lock. */
    public void sendGrouped() {
        orphansByLevel.forEach((level, players) -> LevelRegions.of(level).ownership().runExclusive(() -> {
            for (ServerPlayer player : players) {
                if (!player.hasDisconnected() && !RegionNetworkTick.ownedByRegion(player.connection)) {
                    player.connection.chunkSender.sendNextChunks(player);
                }
            }
        }));

        for (ServerPlayer player : orphans) {
            player.connection.resumeFlushing();
        }
    }
}
