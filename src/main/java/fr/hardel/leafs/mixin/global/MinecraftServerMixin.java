package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The global drain, once per global tick after the level ticks. Also hooked in {@code tickServer}: its pause-when-empty branch skips {@code tickChildren}, yet diverted tasks must still drain. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$drainGlobalTasks(CallbackInfo callbackInfo) {
        TickingManager ticking = TickingManager.of((MinecraftServer) (Object) this);
        StageTimings globalStages = ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalLevels);
        ticking.globalScheduler().drain();
        globalStages.mark(TickStages.globalDrain);
    }
}
