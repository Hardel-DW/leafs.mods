package fr.hardel.leafs.network;

import net.minecraft.world.level.ChunkPos;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.TickingBinding;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.LevelData;


/** Player network split: owning region drains packets and runs the listener tick; global loop keeps transport. */
public final class RegionNetworkTick {
    private RegionNetworkTick() {
    }

    /** Region tick start: the owned player's packets, stopped if a handler moves the player off-level. */
    public static void drainOnRegion(ServerPlayer player, ServerLevel level) {
        ServerGamePacketListenerImpl listener = player.connection;
        PacketRouting.queueOf(listener).drain(() -> listener.player.level() == level);
    }

    /** Region tick end: the full vanilla listener tick, with vanilla's kick-instead-of-crash catch. */
    public static void tickListenerOnRegion(ServerPlayer player, MinecraftServer server) {
        ServerGamePacketListenerImpl listener = player.connection;
        Connection connection = listener.connection;
        if (connection.isConnecting() || !connection.isConnected()) {
            return;
        }

        try {
            listener.tick();
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

    /** {@code Connection.tick}'s listener half: a game listener ticks on the region owning its player, every other listener here. */
    public static void tickListenerGlobally(TickablePacketListener listener, Runnable original) {
        if (!(listener instanceof ServerGamePacketListenerImpl)) {
            original.run();
        }
    }

    /** The respawn runs on the owner of the respawn spot as that listener's packet-handling thread; this drain ends here, the rest of the queue follows the player. */
    public static boolean divertRespawn(ServerGamePacketListenerImpl listener, ServerboundClientCommandPacket packet) {
        if (packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            return false;
        }

        ServerPlayer player = listener.player;
        MinecraftServer server = player.level().getServer();
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        LevelData.RespawnData spot = config == null ? server.overworld().getRespawnData() : config.respawnData();
        ServerLevel level = server.getLevel(spot.dimension());
        ServerLevel target = level == null ? server.overworld() : level;
        ChunkPos chunk = ChunkPos.containing(spot.pos());
        DeferredTransports transports = TickingBinding.of(target);
        if (transports.owns(chunk.x(), chunk.z()) && PacketRouting.queueOf(listener).handledByCurrentThread()) {
            return false;
        }

        PlayerPacketQueue queue = PacketRouting.queueOf(listener);
        queue.handOver();
        DeferredWork.owner(DeferReason.RESPAWN, transports.stats(), chunk.x(), chunk.z(), () -> queue.handleAs(() -> listener.handleClientCommand(packet)))
            .validIf(listener.connection::isConnected)
            .submit(transports);

        return true;
    }

    /** Paused integrated server: vanilla only drains while paused, no listener tick (solo keepalive is exempt anyway). */
    public static void drainPaused(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level) {
                ServerGamePacketListenerImpl listener = player.connection;
                PacketRouting.queueOf(listener).drain(() -> listener.player.level() == level);
            }
        }
    }
}
