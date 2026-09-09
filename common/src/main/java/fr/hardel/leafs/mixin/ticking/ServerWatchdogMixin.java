package fr.hardel.leafs.mixin.ticking;

import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Neutralizes the vanilla watchdog: it measures one game thread that no longer exists as such. */
@Mixin(ServerWatchdog.class)
public abstract class ServerWatchdogMixin {

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void leafs$neutralise(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }
}
