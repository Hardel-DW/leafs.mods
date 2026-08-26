package fr.hardel.leafs.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The global half of the send-chunks section: the players no region owns, one snapshot per tick, suspend and resume balanced on it. */
public final class OrphanNetworkSweep {
    private final Map<ServerLevel, List<ServerPlayer>> orphansByLevel = new LinkedHashMap<>();
    private final List<ServerPlayer> orphans = new ArrayList<>();

    /** The suspend pass sees only this capture; suspend and resume stay balanced on the same snapshot. */
    public List<ServerPlayer> captureOrphans(List<ServerPlayer> players) {
        orphansByLevel.clear();
        orphans.clear();
        for (ServerPlayer player : players) {
            if (!RegionNetworkTick.ownedByRegion(player)) {
                orphans.add(player);
                orphansByLevel.computeIfAbsent(player.level(), _ -> new ArrayList<>()).add(player);
            }
        }

        return orphans;
    }

    /** A player a region adopted or a disconnect removed since the capture skips his send; the claim re-reads under the lock. */
    public void sendGrouped() {
        orphansByLevel.forEach((level, players) -> {
            for (ServerPlayer player : players) {
                if (!player.hasDisconnected() && !RegionNetworkTick.ownedByRegion(player)) {
                    player.connection.chunkSender.sendNextChunks(player);
                }
            }
        });

        for (ServerPlayer player : orphans) {
            player.connection.resumeFlushing();
        }
    }
}
