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

    /** Region-tick sends batch on the channel; flushing happens on the global loop's cadence. */
    public static boolean scopedFlush(boolean vanillaFlush) {
        return vanillaFlush && !(RegionContext.current() instanceof RegionContext.Region);
    }

    /** Off the server thread the blocking teardown deadlocks; it queues fire-and-forget instead. */
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
