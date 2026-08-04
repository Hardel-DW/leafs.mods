package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only - rate changes reach the region pool through ticking/TickingManager. */
@Mixin(ServerTickRateManager.class)
public abstract class ServerTickRateManagerMixin {

    @Inject(method = "setTickRate", at = @At("TAIL"))
    private void leafs$propagateTickRate(float rate, CallbackInfo callbackInfo) {
        ServerTickRateManager manager = (ServerTickRateManager) (Object) this;
        ((LeafsServerAccess) manager.server).leafs$ticking().setTickPeriodNanos(manager.nanosecondsPerTick());
    }
}
