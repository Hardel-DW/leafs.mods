package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.DeferredWork;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.TickingManager;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;

public final class RegionNetworkTick {
    private RegionNetworkTick() {
    }

    public static void drainOnRegion(ServerPlayer player, ServerLevel level) {
        ServerGamePacketListenerImpl listener = player.connection;
        countIfHeld(level.getServer(), PacketRouting.queueOf(listener).drain(() -> listener.player.level() == level));
    }

    public static void tickPlayerOnRegion(ServerPlayer player, MinecraftServer server) {
        ServerGamePacketListenerImpl listener = player.connection;
        Connection connection = listener.connection;
        countIfHeld(server, PacketRouting.queueOf(listener).handleAs(() -> {
            if (!connection.isConnecting()) {
                tickListener(listener, connection, server);
            }

            listener.chunkSender.sendNextChunks(player);
            connection.flushChannel();
        }));
    }

    private static void countIfHeld(MinecraftServer server, boolean handled) {
        if (!handled) {
            TickingManager.of(server).metrics().sharedPlayers().increment();
        }
    }

    private static void tickListener(ServerGamePacketListenerImpl listener, Connection connection, MinecraftServer server) {
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

    public static void tickListenerGlobally(TickablePacketListener listener, Runnable original) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            original.run();
            return;
        }

        if (tickedByARegion(game.player)) {
            return;
        }

        MinecraftServer server = game.player.level().getServer();
        countIfHeld(server, PacketRouting.queueOf(game).handleAs(() -> {
            PacketRouting.queueOf(game).drain(() -> !tickedByARegion(game.player));
            original.run();
        }));
    }

    private static boolean tickedByARegion(ServerPlayer player) {
        if (player.isRemoved()) {
            return false;
        }

        LevelRegions regions = LevelRegions.of(player.level());
        ChunkPos chunk = player.chunkPosition();
        return regions.live() && regions.regionizer().regionAt(chunk.x(), chunk.z()) != null;
    }

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
        PlayerPacketQueue queue = PacketRouting.queueOf(listener);
        if (LevelChunks.of(target).owners().holds(chunk.x(), chunk.z()) && queue.handledByCurrentThread()) {
            return false;
        }

        queue.handOver();
        respawnOnTheOwner(target, chunk, queue, listener, packet).submit();
        return true;
    }

    private static DeferredWork respawnOnTheOwner(ServerLevel target, ChunkPos chunk, PlayerPacketQueue queue, ServerGamePacketListenerImpl listener, ServerboundClientCommandPacket packet) {
        return DeferredWork.owner(target, DeferReason.RESPAWN, chunk.x(), chunk.z(), () -> {
            if (!queue.handleAs(() -> listener.handleClientCommand(packet))) {
                respawnOnTheOwner(target, chunk, queue, listener, packet).later();
            }
        }).validIf(listener.connection::isConnected);
    }

    public static void drainPaused(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level) {
                ServerGamePacketListenerImpl listener = player.connection;
                PacketRouting.queueOf(listener).drain(() -> listener.player.level() == level);
            }
        }
    }
}
