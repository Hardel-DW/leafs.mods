package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.ownership.TickGuard;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.LevelRegions;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;


/** Player network split: owning region drains packets and runs the listener tick; global loop keeps transport. */
public final class RegionNetworkTick {
    private static final int RESPAWN_BUDGET = 100;

    private RegionNetworkTick() {
    }

    /** Region tick start: the owned player's packets, stopped if a handler moves the player off-level. */
    public static void drainOnRegion(ServerPlayer player, ServerLevel level) {
        ServerGamePacketListenerImpl listener = player.connection;
        PacketRouting.queueOf(listener).drain(() -> listener.player.level() == level);
    }

    /** Region tick end: the full vanilla listener tick, with vanilla's kick-instead-of-crash catch; a chunk refusal is not an error and reaches the guard. */
    public static void tickListenerOnRegion(ServerPlayer player, MinecraftServer server) {
        ServerGamePacketListenerImpl listener = player.connection;
        Connection connection = listener.connection;
        if (connection.isConnecting() || !connection.isConnected()) {
            return;
        }

        try {
            listener.tick();
        } catch (Exception exception) {
            if (TickGuard.isRefusal(exception)) {
                throw exception;
            }

            if (connection.isMemoryConnection()) {
                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
            }

            Leafs.LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(server.logIPs()), exception);
            Component reason = Component.literal("Internal server error");
            connection.send(new ClientboundDisconnectPacket(reason), PacketSendListener.thenRun(() -> connection.disconnect(reason)));
            connection.setReadOnly();
        }
    }

    /** A region owns the player when one owns his chunk; the global loop keeps its hands off him. */
    public static boolean ownedByRegion(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        LevelRegions regions = LevelRegions.of(level);
        ChunkPos chunk = player.chunkPosition();
        return regions.body() != null && regions.regionizer().regionAt(chunk.x(), chunk.z()) != null;
    }

    /** {@code Connection.tick}'s listener half: skipped while a region owns the player, otherwise the complete vanilla tick, nobody else touches his chunks. */
    public static void tickListenerGlobally(TickablePacketListener listener, Runnable original) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            original.run();
            return;
        }

        if (ownedByRegion(game.player)) {
            return;
        }

        PacketRouting.queueOf(game).drain();
        TickGuard.tickOrSkip(ignored -> original.run(), game.player);
    }

        /** The respawn replays on the owner of the respawn spot, as that listener's packet-handling thread; the death level's removal hops back through the primitives. */
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
        DeferredWork.owner(DeferReason.RESPAWN, transports.stats(), chunk.x(), chunk.z(), () -> {
            if (!queue.handleAs(() -> listener.handleClientCommand(packet))) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "Respawn of " + player.getPlainTextName() + " waits for its packet queue");
            }
        }).validIf(listener.connection::isConnected).degraded(RESPAWN_BUDGET).submit(transports);

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
