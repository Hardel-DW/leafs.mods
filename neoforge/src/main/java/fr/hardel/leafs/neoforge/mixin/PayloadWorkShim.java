package fr.hardel.leafs.neoforge.mixin;

import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(ServerPayloadContext.class)
public abstract class PayloadWorkShim {

    @Shadow
    @Final
    private ServerCommonPacketListener listener;

    @ModifyArg(method = "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", index = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private Executor leafs$runOnThePlayerQueue(Executor server) {
        return leafs$playerQueueOr(server);
    }

    @ModifyArg(method = "enqueueWork(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;", index = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private Executor leafs$supplyOnThePlayerQueue(Executor server) {
        return leafs$playerQueueOr(server);
    }

    @Unique
    private Executor leafs$playerQueueOr(Executor server) {
        return listener instanceof ServerGamePacketListenerImpl game ? PacketRouting.playerTaskExecutor(game) : server;
    }
}
