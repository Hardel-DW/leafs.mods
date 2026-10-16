package fr.hardel.leafs.mixin.thread;

import fr.hardel.leafs.thread.GameThreads;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {
    @Shadow
    @Final
    private Thread runningThread;

    @Inject(method = "isSameThread", at = @At("HEAD"), cancellable = true)
    private void leafs$everyGameThreadHandlesPackets(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(GameThreads.sameThreadAs(runningThread));
    }
}
