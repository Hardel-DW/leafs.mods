package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only - Compromise #6 lives in ticking/TickingManager: off-thread server executes land in the global phase. */
@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "execute(Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffThreadServerExecute(Runnable command, CallbackInfo callbackInfo) {
        if ((Object) this instanceof MinecraftServer server && ((LeafsServerAccess) server).leafs$ticking().divertExecute(command)) {
            callbackInfo.cancel();
        }
    }
}
