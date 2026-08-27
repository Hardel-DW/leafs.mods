package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hook only, the logic lives in ticking/TickingManager: off-thread server executes land in the global phase, and the server thread's own pump runs them too. */
@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "pollTask", at = @At("HEAD"), cancellable = true)
    private void leafs$pumpDivertedTasks(CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof MinecraftServer server && TickingManager.of(server).pumpDiverted()) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "execute(Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffThreadServerExecute(Runnable command, CallbackInfo callbackInfo) {
        if ((Object) this instanceof MinecraftServer server && TickingManager.of(server).divertExecute(command)) {
            callbackInfo.cancel();
        }
    }
}
