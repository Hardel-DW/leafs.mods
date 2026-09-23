package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.PacketRouting;
import fr.hardel.leafs.network.PlayerPacketQueue;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @WrapOperation(method = "scheduleIfPossible", at = @At(value = "INVOKE", target = "Ljava/util/Queue;add(Ljava/lang/Object;)Z"))
    private <T extends PacketListener> boolean leafs$routePlayPackets(Queue<Object> queue, Object entry, Operation<Boolean> original, T listener, Packet<T> packet) {
        return PacketRouting.routeToPlayer(listener, packet) || original.call(queue, entry);
    }

    @Inject(method = "isSameThread", at = @At("HEAD"), cancellable = true)
    private void leafs$drainingUnitIsAPacketThread(CallbackInfoReturnable<Boolean> callback) {
        if (PlayerPacketQueue.handlingPackets()) {
            callback.setReturnValue(true);
        }
    }
}
