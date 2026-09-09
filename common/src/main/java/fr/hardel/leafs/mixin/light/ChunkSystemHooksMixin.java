package fr.hardel.leafs.mixin.light;

import ca.spottedleaf.starlight.common.integration.v0.ChunkSystemHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ScalableLux's questions to the chunk system: the ticket storage takes writers from any thread, its tickets stay vanilla's. */
@Mixin(value = ChunkSystemHooks.class, remap = false)
public abstract class ChunkSystemHooksMixin {

    @Inject(method = "isTicketThreadSafe", at = @At("HEAD"), cancellable = true)
    private static void leafs$ticketsTakeAnyThread(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(true);
    }
}
