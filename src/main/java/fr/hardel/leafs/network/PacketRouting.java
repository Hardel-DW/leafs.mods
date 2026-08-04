package fr.hardel.leafs.network;

import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Routes play packets to their player's queue, handled where that queue drains; login/config/handshake
 * listeners keep vanilla's global processor, drained by the global phase.
 */
public final class PacketRouting {

    private PacketRouting() {
    }

    /** {@code PacketProcessor.scheduleIfPossible} hook - catches Fabric's direct submissions too. */
    public static <T extends PacketListener> boolean routeToPlayer(T listener, Packet<T> packet) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        queueOf(game).add(listener, packet);
        return true;
    }

    /**
     * {@code PacketUtils.ensureRunningOnSameThread} hook: true when the current thread is already
     * draining this listener's queue, so it is the owner by construction. Everything else falls through to vanilla's {@code scheduleIfPossible}.
     */
    public static boolean handledByCurrentDrain(PacketListener listener) {
        return listener instanceof ServerGamePacketListenerImpl game && queueOf(game).handledByCurrentThread();
    }

    /** {@code PacketProcessor.isSameThread} hook: a unit draining a player queue is a packet-handling thread. */
    public static boolean currentThreadHandlesPackets() {
        return PlayerPacketQueue.handlingPackets();
    }

    /**
     * The #8 flush scope: a send mid-region-tick never flushes per packet; the global loop's
     * unconditional per-player {@code resumeFlushing} is the flush pump, vanilla's own cadence.
     */
    public static boolean scopedFlush(boolean vanillaFlush) {
        return vanillaFlush && !(RegionContext.current() instanceof RegionContext.Region);
    }

    /**
     * Play connections are ticked by the unit owning their player, i.e. every player the server still
     * lists. A listener whose player already left the list (reconfiguration, awaiting the client ack) stays with the global loop instead.
     */
    public static boolean ticksOnRegion(Connection connection) {
        if (!(connection.getPacketListener() instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        ServerPlayer player = game.player;
        return player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != null;
    }

    static PlayerPacketQueue queueOf(ServerGamePacketListenerImpl listener) {
        return ((GameListenerNetworkAccess) listener).leafs$inboundQueue();
    }
}
