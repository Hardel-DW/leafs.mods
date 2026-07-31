package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only — logic in network/PacketRouting: play packets are routed to their player's queue. */
@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @Inject(method = "scheduleIfPossible", at = @At("HEAD"), cancellable = true)
    private <T extends PacketListener> void leafs$routePlayPackets(T listener, Packet<T> packet, CallbackInfo callbackInfo) {
        if (PacketRouting.routeToPlayer(listener, packet)) {
            callbackInfo.cancel();
        }
    }
}
