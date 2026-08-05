package fr.hardel.leafs.network;

import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.concurrent.Executor;

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
     * Vanilla's disconnect joins on the teardown; off the server thread that join deadlocks a region
     * worker against the global drain, so the teardown queues fire-and-forget instead.
     */
    public static void runTeardown(MinecraftServer server, Runnable teardown, Runnable vanillaBlocking) {
        if (server.isSameThread()) {
            vanillaBlocking.run();
        } else {
            server.execute(teardown);
        }
    }

    /** Executor view of the player's queue: continuations completed on any thread run on the player's owner, in packet order. */
    public static Executor playerTaskExecutor(ServerGamePacketListenerImpl listener) {
        return task -> queueOf(listener).addTask(task);
    }

    static PlayerPacketQueue queueOf(ServerGamePacketListenerImpl listener) {
        return ((GameListenerNetworkAccess) listener).leafs$inboundQueue();
    }
}
