package fr.hardel.leafs.network;

import fr.hardel.leafs.ticking.RegionTickScheduler;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.concurrent.Executor;

public final class PacketRouting {

    private PacketRouting() {
    }

    public static boolean routeToPlayer(PacketProcessor.ListenerAndPacket<?> entry) {
        if (!(entry.listener() instanceof ServerGamePacketListenerImpl game)) {
            return false;
        }

        TickingManager.of(game.player.level().getServer()).metrics().packetsIn().increment();
        queueOf(game).add(entry);
        return true;
    }

    public static boolean scopedFlush(boolean vanillaFlush) {
        return vanillaFlush && !RegionTickScheduler.onWorker();
    }

    public static void runTeardown(MinecraftServer server, Runnable teardown, Runnable vanillaBlocking) {
        if (TickingManager.of(server).onServerThread()) {
            vanillaBlocking.run();
            return;
        }

        server.execute(teardown);
    }

    public static Executor playerTaskExecutor(ServerGamePacketListenerImpl listener) {
        return task -> queueOf(listener).addTask(task);
    }

    static PlayerPacketQueue queueOf(ServerGamePacketListenerImpl listener) {
        return ((GameListenerNetworkAccess) listener).leafs$inboundQueue();
    }
}
