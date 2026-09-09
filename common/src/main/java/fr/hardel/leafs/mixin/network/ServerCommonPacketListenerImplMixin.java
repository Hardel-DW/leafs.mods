package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.PacketRouting;
import fr.hardel.leafs.ticking.TickingManager;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Hook only, the flush scope lives in network/PacketRouting: region-tick sends batch on the channel. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @Shadow
    @Final
    protected MinecraftServer server;

    @WrapOperation(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
    private void leafs$scopeFlushToRegionTick(Connection connection, Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        TickingManager.of(this.server).metrics().packetsOut().increment();
        original.call(connection, packet, listener, PacketRouting.scopedFlush(flush));
    }

    /** Off the server thread the blocking teardown hand-off deadlocks a region worker; it queues instead. */
    @WrapOperation(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void leafs$nonBlockingTeardownOffThread(MinecraftServer server, Runnable teardown, Operation<Void> original) {
        PacketRouting.runTeardown(server, teardown, () -> original.call(server, teardown));
    }
}
