package fr.hardel.leafs.network;

import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The routing decisions behind mixins #4/#5. Play packets go to their player's queue; ownership is
 * answered by region context instead of thread identity, so handlers stay correct on region threads.
 */
public final class PacketRouting {

    private PacketRouting() {
    }

    /** {@code PacketProcessor.scheduleIfPossible} hook — catches Fabric's direct submissions too. */
    public static <T extends PacketListener> boolean routeToPlayer(T listener, Packet<T> packet) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        ((GameListenerNetworkAccess) game).leafs$inboundQueue().add(listener, packet);
        return true;
    }

    /**
     * {@code PacketUtils.ensureRunningOnSameThread} hook. Returns true when handled: either the
     * current thread owns the player (handler proceeds inline) or the packet was queued and the
     * vanilla stackless rethrow is raised.
     */
    public static <T extends PacketListener> boolean ensureOnOwner(Packet<T> packet, T listener) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        if (currentThreadOwns(game.player.level())) {
            return true;
        }

        ((GameListenerNetworkAccess) game).leafs$inboundQueue().add(listener, packet);
        throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
    }

    /** Play connections are ticked by the region owning their player, not by the global connection loop. */
    public static boolean ticksOnRegion(Connection connection) {
        PacketListener listener = connection.getPacketListener();

        return listener != null && listener.protocol() == ConnectionProtocol.PLAY;
    }

    private static boolean currentThreadOwns(ServerLevel level) {
        return ((LeafsServerAccess) level.getServer()).leafs$ticking().currentThreadOwns(level);
    }
}
