package fr.hardel.leafs.mixin.light;

import ca.spottedleaf.starlight.common.thread.SchedulingUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Managed by Leafs: ScalableLux keeps its parallel light queue and never sizes a pool of its own. */
@Mixin(value = SchedulingUtil.class, remap = false)
public abstract class SchedulingUtilMixin {

    @Inject(method = "isExternallyManaged", at = @At("HEAD"), cancellable = true)
    private static void leafs$theChunkPoolSchedules(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(true);
    }
}
