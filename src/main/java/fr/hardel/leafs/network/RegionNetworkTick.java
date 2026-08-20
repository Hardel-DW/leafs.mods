package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ownership.RegionContext;
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

import java.util.concurrent.TimeUnit;

/** Player network split: owning region drains packets and runs the listener tick; global loop keeps transport. */
public final class RegionNetworkTick {
    private static final long OWNER_STALE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private RegionNetworkTick() {
    }

    /** Region tick start: the owned player's packets, stopped if a handler moves the player off-level. */
    public static void drainOnRegion(ServerPlayer player, ServerLevel level) {
        ServerGamePacketListenerImpl listener = player.connection;
        PlayerPacketQueue queue = PacketRouting.queueOf(listener);
        queue.stampRegionOwner();
        queue.drain(() -> listener.player.level() == level);
    }

    /** Region tick end: the full vanilla listener tick, with vanilla's kick-instead-of-crash catch. */
    public static void tickListenerOnRegion(ServerPlayer player, MinecraftServer server) {
        ServerGamePacketListenerImpl listener = player.connection;
        PacketRouting.queueOf(listener).stampRegionOwner();
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

    /** Whether a region ticked this player recently enough that the global loop must keep its hands off. */
    public static boolean ownedByRegion(ServerGamePacketListenerImpl listener) {
        return PacketRouting.queueOf(listener).regionOwnerFresh(OWNER_STALE_NANOS);
    }

    /** {@code Connection.tick}'s listener half: skipped while a region owns it, otherwise the complete vanilla tick under the level exclusion, which is what makes the server thread own the positions its handlers read. */
    public static void tickListenerGlobally(TickablePacketListener listener, Runnable original) {
        if (!(listener instanceof ServerGamePacketListenerImpl game) || !(game.player.level() instanceof ServerLevel level)) {
            original.run();
            return;
        }

        if (ownedByRegion(game)) {
            return;
        }

        LevelRegions.of(level).ownership().runExclusive(() -> {
            PacketRouting.queueOf(game).drain();
            TickGuard.tickOrSkip(ignored -> original.run(), game.player);
        });
    }

    /**
     * A respawn moves the player across levels and the handler's tail reads the new instance, so the
     * whole vanilla branch replays in the barrier window; the window runs on the server thread, where
     * the re-entered thread guard passes.
     */
    public static boolean divertRespawn(ServerGamePacketListenerImpl listener, ServerboundClientCommandPacket packet) {
        if (packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
            || !(RegionContext.current() instanceof RegionContext.Region)) {
            return false;
        }

        DeferredTransports transports = TickingBinding.of((ServerLevel) listener.player.level());
        DeferredWork.window(DeferReason.RESPAWN, transports.stats(), () -> listener.handleClientCommand(packet))
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
