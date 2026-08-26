package fr.hardel.leafs.network;

import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.concurrent.Executor;

/** Play packets go to their player's queue; login, config and handshake keep vanilla's global processor. */
public final class PacketRouting {

    private PacketRouting() {
    }

    /** {@code PacketProcessor.scheduleIfPossible} hook, covers Fabric's direct submissions too. */
    public static <T extends PacketListener> boolean routeToPlayer(T listener, Packet<T> packet) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        TickingManager.of(game.player.level().getServer()).metrics().packetsIn().increment();
        queueOf(game).add(listener, packet);
        return true;
    }

    /** {@code PacketUtils.ensureRunningOnSameThread} hook: the thread draining this queue is the owner. */
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
