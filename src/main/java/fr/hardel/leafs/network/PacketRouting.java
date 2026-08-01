package fr.hardel.leafs.network;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The routing decisions behind mixins #4/#5. Play packets go to their player's queue and are handled
 * where that queue drains; login/config/handshake listeners keep vanilla's global processor, which
 * the global phase still drains.
 */
public final class PacketRouting {

    private PacketRouting() {
    }

    /** {@code PacketProcessor.scheduleIfPossible} hook — catches Fabric's direct submissions too. */
    public static <T extends PacketListener> boolean routeToPlayer(T listener, Packet<T> packet) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        queueOf(game).add(listener, packet);
        return true;
    }

    /**
     * {@code PacketUtils.ensureRunningOnSameThread} hook: true when the handler may proceed inline
     * because the current thread is already draining this listener's queue, so it IS the owner by
     * construction. Every other case falls through to vanilla, which routes through
     * {@code scheduleIfPossible} — the single enqueue path, so a closed processor still rejects.
     */
    public static boolean handledByCurrentDrain(PacketListener listener) {
        return listener instanceof ServerGamePacketListenerImpl game && queueOf(game).handledByCurrentThread();
    }

    /** {@code PacketProcessor.isSameThread} hook: a unit draining a player queue is a packet-handling thread. */
    public static boolean currentThreadHandlesPackets() {
        return PlayerPacketQueue.handlingPackets();
    }

    /**
     * Play connections are ticked by the unit owning their player, which is every player the server
     * still lists. A listener whose player already left the list — reconfiguration waits for the
     * client ack with the inbound protocol still on PLAY — stays with the global loop, the only
     * thing then keeping it flushed and reaped.
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
