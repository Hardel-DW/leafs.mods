package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.PacketRouting;
import fr.hardel.leafs.network.PlayerPacketQueue;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @WrapOperation(method = "scheduleIfPossible(Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;add(Ljava/lang/Object;)Z"))
    private boolean leafs$routePlayPackets(Queue<Object> queue, Object entry, Operation<Boolean> original) {
        return PacketRouting.routeToPlayer((PacketProcessor.ListenerAndPacket<?>) entry) || original.call(queue, entry);
    }

    @Inject(method = "isSameThread", at = @At("HEAD"), cancellable = true)
    private void leafs$drainingUnitIsAPacketThread(CallbackInfoReturnable<Boolean> callback) {
        if (PlayerPacketQueue.handlingPackets()) {
            callback.setReturnValue(true);
        }
    }
}
