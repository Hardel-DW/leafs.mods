package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.network.GameListenerNetworkAccess;
import fr.hardel.leafs.network.PacketRouting;
import fr.hardel.leafs.network.PlayerPacketQueue;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements GameListenerNetworkAccess {

    @Shadow
    public ServerPlayer player;

    @Unique
    private final PlayerPacketQueue leafs$inboundQueue = new PlayerPacketQueue();

    @Override
    public PlayerPacketQueue leafs$inboundQueue() {
        return leafs$inboundQueue;
    }

    @WrapMethod(method = "tickPlayer")
    private boolean leafs$tickOnLandedChunks(Operation<Boolean> original) {
        ChunkPos chunk = player.chunkPosition();
        if (!RegionChunkAccess.fullAround(player.level().getChunkSource().chunkMap, chunk.x(), chunk.z())) {
            return false;
        }

        return original.call();
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/FutureChain;<init>(Ljava/util/concurrent/Executor;)V"))
    private Executor leafs$chatChainOnTheOwner(Executor server) {
        return PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this);
    }

    @ModifyArg(method = {"handleEditBook", "handleSignUpdate"}, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor leafs$filteredTextOnTheOwner(Executor server) {
        return PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this);
    }

    @WrapOperation(method = "tryHandleChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V"))
    private void leafs$chatHandlerOnTheOwner(MinecraftServer server, Runnable chatHandler, Operation<Void> original, @Local(argsOnly = true) boolean isCommand) {
        if (isCommand) {
            original.call(server, chatHandler);
        } else {
            PacketRouting.playerTaskExecutor((ServerGamePacketListenerImpl) (Object) this).execute(chatHandler);
        }
    }

    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void leafs$respawnOnTheOwner(ServerboundClientCommandPacket packet, CallbackInfo callbackInfo) {
        if (RegionNetworkTick.divertRespawn((ServerGamePacketListenerImpl) (Object) this, packet)) {
            callbackInfo.cancel();
        }
    }

    @WrapMethod(method = "onDisconnect")
    private void leafs$lockThePlayerOnDisconnect(DisconnectionDetails details, Operation<Void> original) {
        RegionBorrow.atContact(player);
        original.call(details);
    }
}
