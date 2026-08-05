package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.network.GameListenerNetworkAccess;
import fr.hardel.leafs.network.PacketRouting;
import fr.hardel.leafs.network.PlayerPacketQueue;
import fr.hardel.leafs.network.RegionNetworkTick;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/** Carries the player's inbound queue; handler continuations route back to it, respawn replays in the window. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements GameListenerNetworkAccess {

    @Unique
    private final PlayerPacketQueue leafs$inboundQueue = new PlayerPacketQueue();

    @Override
    public PlayerPacketQueue leafs$inboundQueue() {
        return leafs$inboundQueue;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/FutureChain;<init>(Ljava/util/concurrent/Executor;)V"))
    private Executor leafs$chatChainOnTheOwner(Executor server) {
        return PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this);
    }

    @ModifyArg(method = {"handleEditBook", "handleSignUpdate"}, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor leafs$filteredTextOnTheOwner(Executor server) {
        return PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this);
    }

    /** Chat state is player-scoped and runs on the owner; commands reach arbitrary chunks (/locate sync-loads) and keep the global phase. */
    @WrapOperation(method = "tryHandleChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V"))
    private void leafs$chatHandlerOnTheOwner(MinecraftServer server, Runnable chatHandler, Operation<Void> original, @Local(argsOnly = true) boolean isCommand) {
        if (isCommand) {
            original.call(server, chatHandler);
        } else {
            PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this).execute(chatHandler);
        }
    }

    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void leafs$respawnThroughTheWindow(ServerboundClientCommandPacket packet, CallbackInfo callbackInfo) {
        if (RegionNetworkTick.divertRespawn((ServerGamePacketListenerImpl) (Object) this, packet)) {
            callbackInfo.cancel();
        }
    }

    /** Sends run on the global loop; the ack joins them there so the chunk sender stays single-threaded. */
    @WrapOperation(method = "handleChunkBatchReceived", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;onChunkBatchReceivedByClient(F)V"))
    private void leafs$chunkAckOnTheSenderThread(PlayerChunkSender sender, float desiredBatches, Operation<Void> original) {
        MinecraftServer server = ((ServerGamePacketListenerImpl) (Object) this).player.level().getServer();
        if (server.isSameThread()) {
            original.call(sender, desiredBatches);
        } else {
            server.execute(() -> original.call(sender, desiredBatches));
        }
    }
}
