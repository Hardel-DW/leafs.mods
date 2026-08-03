package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only - logic in network/PacketRouting: ownership is the drain in progress, not thread identity. */
@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", at = @At("HEAD"), cancellable = true)
    private static void leafs$ownedByTheDrainInProgress(Packet<?> packet, PacketListener listener, PacketProcessor processor, CallbackInfo callbackInfo) {
        if (PacketRouting.handledByCurrentDrain(listener)) {
            callbackInfo.cancel();
        }
    }
}
