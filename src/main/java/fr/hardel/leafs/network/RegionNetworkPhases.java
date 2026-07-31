package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ticking.LevelTickPhases;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The region-side halves of the network split: inbound packets drained before the level tick,
 * play connections ticked after it (vanilla order inside a tick), replicating the global loop's
 * error handling. Closed connections are left to the global loop, which keeps the disconnection path.
 */
public final class RegionNetworkPhases implements LevelTickPhases {

    @Override
    public void beforeLevelTick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ((GameListenerNetworkAccess) player.connection).leafs$inboundQueue().drain();
        }
    }

    @Override
    public void afterLevelTick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        for (ServerPlayer player : level.players()) {
            tickPlayConnection(server, player.connection.connection);
        }
    }

    private void tickPlayConnection(MinecraftServer server, Connection connection) {
        if (connection.isConnecting() || !connection.isConnected()) {
            return;
        }

        try {
            connection.tick();
        } catch (Exception exception) {
            if (connection.isMemoryConnection()) {
                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
            }

            Leafs.LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(server.logIPs()), exception);
            Component reason = Component.literal("Internal server error");
            connection.send(new ClientboundDisconnectPacket(reason), PacketSendListener.thenRun(() -> connection.disconnect(reason)));
            connection.setReadOnly();
        }
    }
}
